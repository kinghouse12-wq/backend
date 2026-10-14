from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from starlette.exceptions import HTTPException
from starlette.responses import JSONResponse

from workboard.domain.errors import (
    AuthenticationFailed,
    Conflict,
    InvalidTransition,
    NotFound,
    WorkboardError,
)


def register_handlers(app: FastAPI) -> None:
    @app.exception_handler(WorkboardError)
    async def business_error(request: Request, error: WorkboardError) -> JSONResponse:
        status = {
            AuthenticationFailed: 401,
            NotFound: 404,
            Conflict: 409,
            InvalidTransition: 422,
        }.get(type(error), 400)
        headers = {"WWW-Authenticate": "Bearer"} if status == 401 else None
        return JSONResponse(
            {
                "error": {"code": error.code, "message": str(error)},
                "request_id": request.state.request_id,
            },
            status_code=status,
            headers=headers,
        )

    @app.exception_handler(RequestValidationError)
    async def validation_error(request: Request, error: RequestValidationError) -> JSONResponse:
        # Do not echo raw inputs (particularly credentials) into error payloads.
        details = [
            {
                "field": ".".join(str(part) for part in item["loc"]),
                "message": item["msg"],
                "type": item["type"],
            }
            for item in error.errors()
        ]
        return JSONResponse(
            {
                "error": {
                    "code": "validation_error",
                    "message": "Invalid request.",
                    "details": details,
                },
                "request_id": request.state.request_id,
            },
            status_code=422,
        )

    @app.exception_handler(HTTPException)
    async def http_error(request: Request, error: HTTPException) -> JSONResponse:
        return JSONResponse(
            {
                "error": {"code": "http_error", "message": str(error.detail)},
                "request_id": request.state.request_id,
            },
            status_code=error.status_code,
            headers=error.headers,
        )
