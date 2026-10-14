from dataclasses import replace
from uuid import UUID, uuid4

import pytest
from conftest import PASSWORD

from workboard.domain.entities import User, utc_now
from workboard.domain.errors import Conflict
from workboard.infrastructure.repositories import SqlUsers


def test_database_rejects_duplicate_email_even_without_service_precheck(client, headers):
    with client.app.state.session_factory() as session:
        repository = SqlUsers(session)
        with pytest.raises(Conflict):
            repository.add(
                User(
                    uuid4(),
                    "owner@example.com",
                    client.app.state.passwords.hash(PASSWORD),
                    utc_now(),
                )
            )
        # The adapter rolls back a failed flush; this session remains usable.
        assert repository.by_email("owner@example.com") is not None


def test_atomic_repository_rejects_stale_snapshot(client, headers, project, task):
    from workboard.infrastructure.repositories import SqlTasks

    with client.app.state.session_factory() as session:
        repository = SqlTasks(session)
        snapshot = repository.get(UUID(task["id"]), UUID(project["id"]))
        assert repository.update(replace(snapshot, title="First", version=2), 1)
        assert not repository.update(replace(snapshot, title="Stale", version=2), 1)
        session.commit()


def test_service_handles_race_after_initial_version_read(
    client, headers, project, task, monkeypatch
):
    from workboard.infrastructure.repositories import SqlTasks

    monkeypatch.setattr(SqlTasks, "update", lambda *args: False)
    path = f"/api/v1/projects/{project['id']}/tasks/{task['id']}"
    response = client.patch(
        path, headers=headers, json={"title": "Lost race", "expected_version": 1}
    )
    assert response.status_code == 409
    assert client.get(path, headers=headers).json()["version"] == 1


def test_token_response_reports_expiry(client, headers):
    response = client.post(
        "/api/v1/auth/token",
        json={
            "email": "owner@example.com",
            "password": PASSWORD,
        },
    )
    assert response.json()["expires_in"] == 900
    assert response.json()["token_type"] == "bearer"
