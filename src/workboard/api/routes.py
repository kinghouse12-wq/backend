from typing import Annotated, cast
from uuid import UUID

from fastapi import APIRouter, Query, Request, Response
from sqlalchemy import Engine, text
from sqlalchemy.exc import SQLAlchemyError
from starlette.responses import JSONResponse

from workboard.api.dependencies import AuthDep, CurrentUser, ProjectsDep, TasksDep
from workboard.api.schemas import (
    Credentials,
    ErrorOut,
    Page,
    ProjectCreate,
    ProjectOut,
    TaskCreate,
    TaskEventOut,
    TaskOut,
    TaskPatch,
    TokenOut,
    UserOut,
)
from workboard.application.services import TaskUpdate
from workboard.domain.entities import Project, Task, TaskStatus, User
from workboard.infrastructure.config import Settings

router = APIRouter(
    prefix="/api/v1",
    responses={code: {"model": ErrorOut} for code in (401, 404, 409, 422, 500)},
)
health = APIRouter(prefix="/health", tags=["Health"])
Limit = Annotated[int, Query(ge=1, le=100)]
Offset = Annotated[int, Query(ge=0, le=10000)]


@router.post("/auth/register", response_model=UserOut, status_code=201, tags=["Authentication"])
def register(body: Credentials, auth: AuthDep) -> User:
    """Create an account. Passwords are stored as Argon2id hashes."""
    return auth.register(str(body.email), body.password)


@router.post("/auth/token", response_model=TokenOut, tags=["Authentication"])
def token(body: Credentials, request: Request, auth: AuthDep) -> TokenOut:
    """Exchange JSON credentials for a short-lived bearer JWT."""
    settings = cast(Settings, request.app.state.settings)
    return TokenOut(
        access_token=auth.login(str(body.email), body.password),
        expires_in=settings.token_minutes * 60,
    )


@router.get("/auth/me", response_model=UserOut, tags=["Authentication"])
def me(user: CurrentUser) -> User:
    return user


@router.post("/projects", response_model=ProjectOut, status_code=201, tags=["Projects"])
def create_project(body: ProjectCreate, user: CurrentUser, service: ProjectsDep) -> Project:
    return service.create(user.id, body.name, body.description)


@router.get("/projects", response_model=Page[ProjectOut], tags=["Projects"])
def list_projects(
    user: CurrentUser, service: ProjectsDep, limit: Limit = 20, offset: Offset = 0
) -> Page[ProjectOut]:
    items, total = service.page(user.id, limit, offset)
    return Page(
        items=[ProjectOut.model_validate(p) for p in items], total=total, limit=limit, offset=offset
    )


@router.get("/projects/{project_id}", response_model=ProjectOut, tags=["Projects"])
def get_project(project_id: UUID, user: CurrentUser, service: ProjectsDep) -> Project:
    return service.get(project_id, user.id)


@router.delete("/projects/{project_id}", status_code=204, tags=["Projects"])
def delete_project(project_id: UUID, user: CurrentUser, service: ProjectsDep) -> Response:
    """Delete your project and cascade its tasks and task history."""
    service.delete(project_id, user.id)
    return Response(status_code=204)


@router.post(
    "/projects/{project_id}/tasks", response_model=TaskOut, status_code=201, tags=["Tasks"]
)
def create_task(project_id: UUID, body: TaskCreate, user: CurrentUser, service: TasksDep) -> Task:
    return service.create(project_id, user.id, body.title, body.description)


@router.get("/projects/{project_id}/tasks", response_model=Page[TaskOut], tags=["Tasks"])
def list_tasks(
    project_id: UUID,
    user: CurrentUser,
    service: TasksDep,
    status: TaskStatus | None = None,
    limit: Limit = 20,
    offset: Offset = 0,
) -> Page[TaskOut]:
    items, total = service.page(project_id, user.id, status, limit, offset)
    return Page(
        items=[TaskOut.model_validate(t) for t in items], total=total, limit=limit, offset=offset
    )


@router.get("/projects/{project_id}/tasks/{task_id}", response_model=TaskOut, tags=["Tasks"])
def get_task(project_id: UUID, task_id: UUID, user: CurrentUser, service: TasksDep) -> Task:
    return service.get(project_id, task_id, user.id)


@router.patch("/projects/{project_id}/tasks/{task_id}", response_model=TaskOut, tags=["Tasks"])
def update_task(
    project_id: UUID, task_id: UUID, body: TaskPatch, user: CurrentUser, service: TasksDep
) -> Task:
    """Update using the version last read; stale versions return 409, invalid transitions 422."""
    changes = TaskUpdate(
        expected_version=body.expected_version,
        title=body.title,
        description=body.description,
        status=body.status,
        replace_description="description" in body.model_fields_set,
    )
    return service.update(project_id, task_id, user.id, changes)


@router.get(
    "/projects/{project_id}/tasks/{task_id}/events",
    response_model=Page[TaskEventOut],
    tags=["Tasks"],
)
def task_events(
    project_id: UUID,
    task_id: UUID,
    user: CurrentUser,
    service: TasksDep,
    limit: Limit = 20,
    offset: Offset = 0,
) -> Page[TaskEventOut]:
    """Read status changes in version order; creation is the first event."""
    items, total = service.events(project_id, task_id, user.id, limit, offset)
    return Page(
        items=[TaskEventOut.model_validate(e) for e in items],
        total=total,
        limit=limit,
        offset=offset,
    )


@health.get("/live")
def live() -> dict[str, str]:
    return {"status": "alive"}


@health.get("/ready", response_model=None, responses={503: {"description": "Database unavailable"}})
def ready(request: Request) -> dict[str, str] | JSONResponse:
    engine = cast(Engine, request.app.state.engine)
    try:
        with engine.connect() as connection:
            connection.execute(text("SELECT 1"))
    except SQLAlchemyError:
        return JSONResponse({"status": "not_ready"}, status_code=503)
    return {"status": "ready"}
