from collections.abc import AsyncIterator, Awaitable, Callable
from contextlib import asynccontextmanager
from time import perf_counter
from uuid import UUID, uuid4

from fastapi import FastAPI, Request
from sqlalchemy.orm import sessionmaker
from starlette.middleware.cors import CORSMiddleware
from starlette.middleware.trustedhost import TrustedHostMiddleware
from starlette.responses import JSONResponse, Response

from workboard.api.errors import register_handlers
from workboard.api.routes import health, router
from workboard.infrastructure.config import Settings, get_settings
from workboard.infrastructure.database import build_engine
from workboard.infrastructure.logging import request_logger
from workboard.infrastructure.security import ArgonPasswords, JwtTokens


def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or get_settings()
    engine = build_engine(settings.database_url)

    @asynccontextmanager
    async def lifespan(_: FastAPI) -> AsyncIterator[None]:
        yield
        engine.dispose()

    app = FastAPI(
        title="Workboard API",
        version="1.0.0",
        description="Project ownership, explicit task workflows, and version-safe updates.",
        lifespan=lifespan,
        openapi_tags=[
            {"name": "Authentication"},
            {"name": "Projects"},
            {"name": "Tasks"},
            {"name": "Health"},
        ],
    )
    app.state.settings = settings
    app.state.engine = engine
    app.state.session_factory = sessionmaker(engine, expire_on_commit=False)
    app.state.passwords = ArgonPasswords()
    app.state.tokens = JwtTokens(settings)
    logger = request_logger()
    app.add_middleware(TrustedHostMiddleware, allowed_hosts=settings.allowed_hosts)
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origins,
        allow_methods=["GET", "POST", "PATCH", "DELETE"],
        allow_headers=["Authorization", "Content-Type", "X-Request-ID"],
        expose_headers=["X-Request-ID"],
    )

    @app.middleware("http")
    async def trace_request(
        request: Request, call_next: Callable[[Request], Awaitable[Response]]
    ) -> Response:
        try:
            request_id = str(UUID(request.headers.get("X-Request-ID", "")))
        except ValueError:
            request_id = str(uuid4())
        request.state.request_id = request_id
        start = perf_counter()
        try:
            response = await call_next(request)
        except Exception as error:
            logger.error(
                "request_failed",
                extra={
                    "context": {
                        "request_id": request_id,
                        "error_type": type(error).__name__,
                    }
                },
            )
            response = JSONResponse(
                {
                    "error": {"code": "internal_error", "message": "An unexpected error occurred."},
                    "request_id": request_id,
                },
                status_code=500,
            )
        response.headers["X-Request-ID"] = request_id
        logger.info(
            "request_completed",
            extra={
                "context": {
                    "request_id": request_id,
                    "method": request.method,
                    "path": request.url.path,
                    "status": response.status_code,
                    "duration_ms": round((perf_counter() - start) * 1000, 2),
                }
            },
        )
        return response

    register_handlers(app)
    app.include_router(router)
    app.include_router(health)
    return app
