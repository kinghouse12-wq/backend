from dataclasses import asdict
from uuid import UUID

from sqlalchemy import delete, func, select, update
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from workboard.application.ports import Projects, Tasks, Users
from workboard.domain.entities import Project, Task, TaskEvent, TaskStatus, User
from workboard.domain.errors import Conflict
from workboard.infrastructure.models import ProjectRow, TaskEventRow, TaskRow, UserRow


def user_entity(row: UserRow) -> User:
    return User(row.id, row.email, row.password_hash, row.created_at)


def project_entity(row: ProjectRow) -> Project:
    return Project(row.id, row.owner_id, row.name, row.description, row.created_at)


def task_entity(row: TaskRow) -> Task:
    return Task(
        row.id,
        row.project_id,
        row.title,
        row.description,
        TaskStatus(row.status),
        row.version,
        row.created_at,
        row.updated_at,
    )


def event_entity(row: TaskEventRow) -> TaskEvent:
    return TaskEvent(
        row.id,
        row.task_id,
        row.actor_id,
        TaskStatus(row.from_status) if row.from_status else None,
        TaskStatus(row.to_status),
        row.version,
        row.created_at,
    )


class SqlUsers:
    def __init__(self, session: Session):
        self.session = session

    def by_email(self, email: str) -> User | None:
        row = self.session.scalar(select(UserRow).where(UserRow.email == email))
        return user_entity(row) if row else None

    def by_id(self, user_id: UUID) -> User | None:
        row = self.session.get(UserRow, user_id)
        return user_entity(row) if row else None

    def add(self, user: User) -> None:
        self.session.add(UserRow(**asdict(user)))
        try:
            self.session.flush()
        except IntegrityError as error:
            self.session.rollback()
            raise Conflict("Email is already registered.") from error


class SqlProjects:
    def __init__(self, session: Session):
        self.session = session

    def owned(self, project_id: UUID, owner_id: UUID) -> Project | None:
        row = self.session.scalar(
            select(ProjectRow).where(ProjectRow.id == project_id, ProjectRow.owner_id == owner_id)
        )
        return project_entity(row) if row else None

    def page(self, owner_id: UUID, limit: int, offset: int) -> tuple[list[Project], int]:
        predicate = ProjectRow.owner_id == owner_id
        total = self.session.scalar(select(func.count()).select_from(ProjectRow).where(predicate))
        rows = self.session.scalars(
            select(ProjectRow)
            .where(predicate)
            .order_by(ProjectRow.created_at.desc(), ProjectRow.id.desc())
            .limit(limit)
            .offset(offset)
        )
        return [project_entity(row) for row in rows], int(total or 0)

    def add(self, project: Project) -> None:
        self.session.add(ProjectRow(**asdict(project)))
        self.session.flush()

    def delete(self, project_id: UUID) -> None:
        self.session.execute(delete(ProjectRow).where(ProjectRow.id == project_id))


class SqlTasks:
    def __init__(self, session: Session):
        self.session = session

    def get(self, task_id: UUID, project_id: UUID) -> Task | None:
        row = self.session.scalar(
            select(TaskRow).where(TaskRow.id == task_id, TaskRow.project_id == project_id)
        )
        return task_entity(row) if row else None

    def page(
        self, project_id: UUID, status: TaskStatus | None, limit: int, offset: int
    ) -> tuple[list[Task], int]:
        query = select(TaskRow).where(TaskRow.project_id == project_id)
        if status is not None:
            query = query.where(TaskRow.status == status)
        total = self.session.scalar(select(func.count()).select_from(query.subquery()))
        rows = self.session.scalars(
            query.order_by(TaskRow.created_at.desc(), TaskRow.id.desc()).limit(limit).offset(offset)
        )
        return [task_entity(row) for row in rows], int(total or 0)

    def add(self, task: Task) -> None:
        self.session.add(TaskRow(**asdict(task)))
        self.session.flush()

    def update(self, task: Task, expected_version: int) -> bool:
        values = asdict(task)
        values.pop("id")
        values.pop("project_id")
        values.pop("created_at")
        result = self.session.scalar(
            update(TaskRow)
            .where(
                TaskRow.id == task.id,
                TaskRow.project_id == task.project_id,
                TaskRow.version == expected_version,
            )
            .values(**values)
            .returning(TaskRow.id)
            .execution_options(synchronize_session=False)
        )
        return result is not None

    def add_event(self, event: TaskEvent) -> None:
        self.session.add(TaskEventRow(**asdict(event)))

    def events(self, task_id: UUID, limit: int, offset: int) -> tuple[list[TaskEvent], int]:
        predicate = TaskEventRow.task_id == task_id
        total = self.session.scalar(select(func.count()).select_from(TaskEventRow).where(predicate))
        rows = self.session.scalars(
            select(TaskEventRow)
            .where(predicate)
            .order_by(TaskEventRow.version)
            .limit(limit)
            .offset(offset)
        )
        return [event_entity(row) for row in rows], int(total or 0)


class SqlUnitOfWork:
    def __init__(self, session: Session):
        self.session = session
        self.users: Users = SqlUsers(session)
        self.projects: Projects = SqlProjects(session)
        self.tasks: Tasks = SqlTasks(session)

    def commit(self) -> None:
        self.session.commit()
