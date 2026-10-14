from uuid import uuid4

import pytest
from conftest import register_and_login
from sqlalchemy import func, select

from workboard.infrastructure.models import TaskEventRow, TaskRow
from workboard.infrastructure.repositories import SqlTasks


def task_path(project, task):
    return f"/api/v1/projects/{project['id']}/tasks/{task['id']}"


def test_projects_are_paginated_and_owner_scoped(client, headers, project):
    for name in ["Second", "Third"]:
        assert (
            client.post("/api/v1/projects", headers=headers, json={"name": name}).status_code == 201
        )
    first = client.get("/api/v1/projects?limit=2", headers=headers).json()
    second = client.get("/api/v1/projects?limit=2&offset=2", headers=headers).json()
    assert first["total"] == second["total"] == 3
    assert len(first["items"]) == 2 and len(second["items"]) == 1
    assert {p["id"] for p in first["items"]}.isdisjoint(p["id"] for p in second["items"])
    outsider = register_and_login(client, "outsider@example.com")
    assert client.get("/api/v1/projects", headers=outsider).json()["total"] == 0
    assert client.get(f"/api/v1/projects/{project['id']}", headers=headers).status_code == 200


@pytest.mark.parametrize(
    "method,suffix,body",
    [
        ("GET", "", None),
        ("DELETE", "", None),
        ("GET", "/tasks", None),
        ("POST", "/tasks", {"title": "Unauthorized"}),
        ("GET", "/tasks/TASK", None),
        ("GET", "/tasks/TASK/events", None),
        ("PATCH", "/tasks/TASK", {"title": "Unauthorized", "expected_version": 1}),
    ],
)
def test_other_users_cannot_access_or_mutate_resources(client, project, task, method, suffix, body):
    outsider = register_and_login(client, "outsider@example.com")
    path = f"/api/v1/projects/{project['id']}" + suffix.replace("TASK", task["id"])
    response = client.request(method, path, headers=outsider, json=body)
    assert response.status_code == 404
    assert response.json()["error"]["code"] == "not_found"


def test_task_cannot_be_addressed_through_another_project(client, headers, project, task):
    other = client.post("/api/v1/projects", headers=headers, json={"name": "Other"}).json()
    assert client.get(task_path(other, task), headers=headers).status_code == 404
    assert client.get(task_path(project, {"id": str(uuid4())}), headers=headers).status_code == 404
    assert client.get(f"/api/v1/projects/{uuid4()}", headers=headers).status_code == 404


def test_workflow_and_audit_history(client, headers, project, task):
    path = task_path(project, task)
    assert task["version"] == 1 and task["status"] == "todo"
    for version, status in [(1, "in_progress"), (2, "done"), (3, "in_progress")]:
        response = client.patch(
            path, headers=headers, json={"status": status, "expected_version": version}
        )
        assert response.status_code == 200
        assert response.json()["version"] == version + 1
    events = client.get(path + "/events", headers=headers).json()
    assert [e["to_status"] for e in events["items"]] == [
        "todo",
        "in_progress",
        "done",
        "in_progress",
    ]
    assert [e["version"] for e in events["items"]] == [1, 2, 3, 4]
    assert events["items"][0]["from_status"] is None
    page = client.get(path + "/events?limit=1&offset=1", headers=headers).json()
    assert page["total"] == 4 and page["items"][0]["version"] == 2


def test_invalid_transition_leaves_task_and_audit_unchanged(client, headers, project, task):
    path = task_path(project, task)
    response = client.patch(path, headers=headers, json={"status": "done", "expected_version": 1})
    assert response.status_code == 422
    assert response.json()["error"]["code"] == "invalid_transition"
    assert client.get(path, headers=headers).json()["version"] == 1
    assert client.get(path + "/events", headers=headers).json()["total"] == 1


def test_stale_version_cannot_overwrite_newer_edit(client, headers, project, task):
    path = task_path(project, task)
    assert (
        client.patch(
            path, headers=headers, json={"title": "New", "expected_version": 1}
        ).status_code
        == 200
    )
    stale = client.patch(path, headers=headers, json={"title": "Old", "expected_version": 1})
    assert stale.status_code == 409
    assert client.get(path, headers=headers).json()["title"] == "New"
    assert client.get(path + "/events", headers=headers).json()["total"] == 1


def test_description_can_be_cleared_and_same_status_is_not_a_new_event(
    client, headers, project, task
):
    path = task_path(project, task)
    response = client.patch(
        path, headers=headers, json={"description": None, "status": "todo", "expected_version": 1}
    )
    assert response.status_code == 200
    assert response.json()["description"] is None
    assert response.json()["title"] == task["title"]
    assert client.get(path + "/events", headers=headers).json()["total"] == 1


def test_status_filter_and_task_pagination(client, headers, project, task):
    path = task_path(project, task)
    assert (
        client.patch(
            path, headers=headers, json={"status": "in_progress", "expected_version": 1}
        ).status_code
        == 200
    )
    tasks = f"/api/v1/projects/{project['id']}/tasks"
    assert client.post(tasks, headers=headers, json={"title": "Still todo"}).status_code == 201
    assert client.get(tasks + "?status=todo", headers=headers).json()["total"] == 1
    assert client.get(tasks + "?status=done", headers=headers).json()["items"] == []
    page = client.get(tasks + "?limit=1&offset=1", headers=headers).json()
    assert page["total"] == 2 and len(page["items"]) == 1


@pytest.mark.parametrize(
    "payload",
    [
        {"expected_version": 1},
        {"title": ""},
        {"title": " ", "expected_version": 1},
        {"title": None, "expected_version": 1},
        {"status": None, "expected_version": 1},
        {"status": "unknown", "expected_version": 1},
        {"title": "X", "expected_version": 0},
        {"title": "X", "expected_version": 1, "owner_id": "injected"},
    ],
)
def test_invalid_patch_is_rejected(client, headers, project, task, payload):
    assert client.patch(task_path(project, task), headers=headers, json=payload).status_code == 422


@pytest.mark.parametrize(
    "query", ["limit=0", "limit=101", "offset=-1", "offset=10001", "status=bad"]
)
def test_pagination_and_filter_bounds(client, headers, project, query):
    assert (
        client.get(f"/api/v1/projects/{project['id']}/tasks?{query}", headers=headers).status_code
        == 422
    )


def test_titles_are_trimmed_and_extra_fields_rejected(client, headers, project):
    tasks = f"/api/v1/projects/{project['id']}/tasks"
    assert client.post(tasks, headers=headers, json={"title": " "}).status_code == 422
    response = client.post(tasks, headers=headers, json={"title": "  Trim me  "})
    assert response.status_code == 201 and response.json()["title"] == "Trim me"
    assert client.post("/api/v1/projects", headers=headers, json={"name": " "}).status_code == 422
    assert (
        client.post(
            "/api/v1/projects", headers=headers, json={"name": "X", "owner_id": "injected"}
        ).status_code
        == 422
    )


def test_project_delete_cascades_tasks_and_history(client, headers, project, task):
    response = client.delete(f"/api/v1/projects/{project['id']}", headers=headers)
    assert response.status_code == 204 and not response.content
    with client.app.state.session_factory() as session:
        assert session.scalar(select(func.count()).select_from(TaskRow)) == 0
        assert session.scalar(select(func.count()).select_from(TaskEventRow)) == 0
    assert client.delete(f"/api/v1/projects/{project['id']}", headers=headers).status_code == 404


def test_audit_failure_rolls_back_task_update(client, headers, project, task, monkeypatch):
    def fail(*args):
        raise RuntimeError("sensitive exception details")

    monkeypatch.setattr(SqlTasks, "add_event", fail)
    path = task_path(project, task)
    response = client.patch(
        path, headers=headers, json={"status": "in_progress", "expected_version": 1}
    )
    assert response.status_code == 500
    assert "sensitive exception" not in response.text
    current = client.get(path, headers=headers).json()
    assert current["status"] == "todo" and current["version"] == 1
    assert client.get(path + "/events", headers=headers).json()["total"] == 1
