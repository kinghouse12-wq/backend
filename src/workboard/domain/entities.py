from dataclasses import dataclass
from datetime import UTC, datetime
from enum import StrEnum
from uuid import UUID

from workboard.domain.errors import InvalidTransition


def utc_now() -> datetime:
    return datetime.now(UTC)


class TaskStatus(StrEnum):
    TODO = "todo"
    IN_PROGRESS = "in_progress"
    DONE = "done"
    CANCELLED = "cancelled"


TRANSITIONS: dict[TaskStatus, frozenset[TaskStatus]] = {
    TaskStatus.TODO: frozenset({TaskStatus.IN_PROGRESS, TaskStatus.CANCELLED}),
    TaskStatus.IN_PROGRESS: frozenset({TaskStatus.TODO, TaskStatus.DONE, TaskStatus.CANCELLED}),
    TaskStatus.DONE: frozenset({TaskStatus.IN_PROGRESS}),
    TaskStatus.CANCELLED: frozenset({TaskStatus.TODO}),
}


def validate_transition(current: TaskStatus, target: TaskStatus) -> None:
    if current != target and target not in TRANSITIONS[current]:
        raise InvalidTransition(f"Cannot transition from {current} to {target}.")


@dataclass(frozen=True)
class User:
    id: UUID
    email: str
    password_hash: str
    created_at: datetime


@dataclass(frozen=True)
class Project:
    id: UUID
    owner_id: UUID
    name: str
    description: str | None
    created_at: datetime


@dataclass(frozen=True)
class Task:
    id: UUID
    project_id: UUID
    title: str
    description: str | None
    status: TaskStatus
    version: int
    created_at: datetime
    updated_at: datetime


@dataclass(frozen=True)
class TaskEvent:
    id: UUID
    task_id: UUID
    actor_id: UUID
    from_status: TaskStatus | None
    to_status: TaskStatus
    version: int
    created_at: datetime
