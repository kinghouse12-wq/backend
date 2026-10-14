from collections.abc import Iterator
from typing import Annotated, cast

from fastapi import Depends, Request
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy.orm import Session, sessionmaker

from workboard.application.services import AuthService, ProjectService, TaskService
from workboard.domain.entities import User
from workboard.domain.errors import AuthenticationFailed
from workboard.infrastructure.repositories import SqlUnitOfWork
from workboard.infrastructure.security import ArgonPasswords, JwtTokens

bearer = HTTPBearer(auto_error=False)


def get_uow(request: Request) -> Iterator[SqlUnitOfWork]:
    factory = cast(sessionmaker[Session], request.app.state.session_factory)
    with factory() as session:
        # Session.close() rolls back anything not explicitly committed by a use case.
        yield SqlUnitOfWork(session)


UowDep = Annotated[SqlUnitOfWork, Depends(get_uow)]


def get_auth(request: Request, uow: UowDep) -> AuthService:
    return AuthService(
        uow,
        cast(ArgonPasswords, request.app.state.passwords),
        cast(JwtTokens, request.app.state.tokens),
    )


AuthDep = Annotated[AuthService, Depends(get_auth)]


def current_user(
    auth: AuthDep,
    credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(bearer)],
) -> User:
    if credentials is None:
        raise AuthenticationFailed("A bearer access token is required.")
    return auth.authenticate(credentials.credentials)


def get_projects(uow: UowDep) -> ProjectService:
    return ProjectService(uow)


def get_tasks(uow: UowDep) -> TaskService:
    return TaskService(uow)


CurrentUser = Annotated[User, Depends(current_user)]
ProjectsDep = Annotated[ProjectService, Depends(get_projects)]
TasksDep = Annotated[TaskService, Depends(get_tasks)]
