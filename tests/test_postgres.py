from concurrent.futures import ThreadPoolExecutor
from dataclasses import replace
from threading import Barrier
from uuid import UUID

import pytest

from workboard.infrastructure.repositories import SqlTasks


@pytest.mark.postgres
def test_two_concurrent_writers_have_exactly_one_winner(client, headers, project, task):
    if client.app.state.engine.dialect.name != "postgresql":
        pytest.skip("Real competing transactions require PostgreSQL.")
    barrier = Barrier(2)
    factory = client.app.state.session_factory

    def writer(title):
        with factory() as session:
            repository = SqlTasks(session)
            snapshot = repository.get(UUID(task["id"]), UUID(project["id"]))
            barrier.wait(timeout=5)
            won = repository.update(replace(snapshot, title=title, version=2), expected_version=1)
            session.commit()
            return won

    with ThreadPoolExecutor(max_workers=2) as executor:
        results = list(executor.map(writer, ["Writer A", "Writer B"]))
    assert sorted(results) == [False, True]
    response = client.get(f"/api/v1/projects/{project['id']}/tasks/{task['id']}", headers=headers)
    assert response.json()["version"] == 2
    assert response.json()["title"] in {"Writer A", "Writer B"}
