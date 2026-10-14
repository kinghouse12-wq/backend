from dataclasses import dataclass, replace
from uuid import UUID, uuid4

from workboard.application.ports import Passwords, Tokens, UnitOfWork
from workboard.domain.entities import (
    Project,
    Task,
    TaskEvent,
    TaskStatus,
    User,
    utc_now,
    validate_transition,
)
from workboard.domain.errors import AuthenticationFailed, Conflict, NotFound


@dataclass(frozen=True)
class TaskUpdate:
    expected_version: int
    title: str | None = None
    description: str | None = None
    status: TaskStatus | None = None
    replace_description: bool = False


class AuthService:
    def __init__(self, uow: UnitOfWork, passwords: Passwords, tokens: Tokens):
        self.uow, self.passwords, self.tokens = uow, passwords, tokens

    def register(self, email: str, password: str) -> User:
        email = email.lower()
        if self.uow.users.by_email(email):
            raise Conflict("Email is already registered.")
        user = User(uuid4(), email, self.passwords.hash(password), utc_now())
        self.uow.users.add(user)
        self.uow.commit()
        return user

    def login(self, email: str, password: str) -> str:
        user = self.uow.users.by_email(email.lower())
        # The adapter also hashes invalid-user requests to reduce timing differences.
        valid = self.passwords.verify(password, user.password_hash if user else None)
        if user is None or not valid:
            raise AuthenticationFailed("Invalid email or password.")
        return self.tokens.issue(user.id)

    def authenticate(self, token: str) -> User:
        user = self.uow.users.by_id(self.tokens.subject(token))
        if user is None:
            raise AuthenticationFailed("Invalid or expired access token.")
        return user


class ProjectService:
    def __init__(self, uow: UnitOfWork):
        self.uow = uow

    def get(self, project_id: UUID, owner_id: UUID) -> Project:
        project = self.uow.projects.owned(project_id, owner_id)
        if project is None:
            # Do not disclose whether another user's resource exists.
            raise NotFound("Project not found.")
        return project

    def create(self, owner_id: UUID, name: str, description: str | None) -> Project:
        project = Project(uuid4(), owner_id, name, description, utc_now())
        self.uow.projects.add(project)
        self.uow.commit()
        return project

    def page(self, owner_id: UUID, limit: int, offset: int) -> tuple[list[Project], int]:
        return self.uow.projects.page(owner_id, limit, offset)

    def delete(self, project_id: UUID, owner_id: UUID) -> None:
        self.get(project_id, owner_id)
        self.uow.projects.delete(project_id)
        self.uow.commit()


class TaskService:
    def __init__(self, uow: UnitOfWork):
        self.uow = uow
        self.projects = ProjectService(uow)

    def get(self, project_id: UUID, task_id: UUID, owner_id: UUID) -> Task:
        self.projects.get(project_id, owner_id)
        task = self.uow.tasks.get(task_id, project_id)
        if task is None:
            raise NotFound("Task not found.")
        return task

    def create(self, project_id: UUID, owner_id: UUID, title: str, description: str | None) -> Task:
        self.projects.get(project_id, owner_id)
        now = utc_now()
        task = Task(uuid4(), project_id, title, description, TaskStatus.TODO, 1, now, now)
        self.uow.tasks.add(task)
        self.uow.tasks.add_event(
            TaskEvent(uuid4(), task.id, owner_id, None, task.status, task.version, now)
        )
        self.uow.commit()
        return task

    def update(self, project_id: UUID, task_id: UUID, owner_id: UUID, changes: TaskUpdate) -> Task:
        task = self.get(project_id, task_id, owner_id)
        if task.version != changes.expected_version:
            raise Conflict("Task changed. Fetch the latest version and retry.")
        status = changes.status if changes.status is not None else task.status
        validate_transition(task.status, status)
        updated = replace(
            task,
            title=changes.title if changes.title is not None else task.title,
            description=changes.description if changes.replace_description else task.description,
            status=status,
            version=task.version + 1,
            updated_at=utc_now(),
        )
        # The adapter uses UPDATE ... WHERE version = expected_version, atomically.
        if not self.uow.tasks.update(updated, changes.expected_version):
            raise Conflict("Task changed. Fetch the latest version and retry.")
        if status != task.status:
            self.uow.tasks.add_event(
                TaskEvent(
                    uuid4(),
                    task.id,
                    owner_id,
                    task.status,
                    status,
                    updated.version,
                    updated.updated_at,
                )
            )
        # The task update and its status event commit together, or neither does.
        self.uow.commit()
        return updated

    def page(
        self, project_id: UUID, owner_id: UUID, status: TaskStatus | None, limit: int, offset: int
    ) -> tuple[list[Task], int]:
        self.projects.get(project_id, owner_id)
        return self.uow.tasks.page(project_id, status, limit, offset)

    def events(
        self, project_id: UUID, task_id: UUID, owner_id: UUID, limit: int, offset: int
    ) -> tuple[list[TaskEvent], int]:
        self.get(project_id, task_id, owner_id)
        return self.uow.tasks.events(task_id, limit, offset)
