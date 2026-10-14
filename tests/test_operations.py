import json
from uuid import uuid4

import pytest
from pydantic import SecretStr, ValidationError
from sqlalchemy.exc import OperationalError

from workboard.infrastructure.config import Settings
from workboard.infrastructure.logging import JsonFormatter


def test_health_and_openapi(client):
    assert client.get("/health/live").json() == {"status": "alive"}
    assert client.get("/health/ready").json() == {"status": "ready"}
    schema = client.get("/openapi.json").json()
    assert schema["info"]["title"] == "Workboard API"
    assert schema["components"]["securitySchemes"]["HTTPBearer"]["scheme"] == "bearer"
    assert "/api/v1/projects/{project_id}/tasks/{task_id}" in schema["paths"]
    assert client.get("/docs").status_code == 200


def test_readiness_fails_when_database_is_unavailable(client, monkeypatch):
    def unavailable(*args):
        raise OperationalError("connection", {}, RuntimeError("offline"))

    monkeypatch.setattr(client.app.state.engine, "connect", unavailable)
    response = client.get("/health/ready")
    assert response.status_code == 503
    assert response.json() == {"status": "not_ready"}
    assert client.get("/health/live").status_code == 200


def test_request_ids_are_validated_and_propagated(client):
    request_id = str(uuid4())
    response = client.get("/missing", headers={"X-Request-ID": request_id})
    assert response.status_code == 404
    assert response.headers["x-request-id"] == response.json()["request_id"] == request_id
    response = client.get("/health/live", headers={"X-Request-ID": "unsafe-value"})
    assert (
        str(__import__("uuid").UUID(response.headers["x-request-id"]))
        == response.headers["x-request-id"]
    )


@pytest.mark.parametrize("secret", ["short", "replace-me-with-a-long-but-known-placeholder-secret"])
def test_settings_reject_weak_or_placeholder_secrets(secret):
    with pytest.raises(ValidationError):
        Settings(jwt_secret=SecretStr(secret), _env_file=None)


def test_logs_are_structured():
    import logging

    record = logging.LogRecord("test", logging.INFO, "", 0, "request_completed", (), None)
    record.context = {"request_id": "abc", "status": 200}
    output = json.loads(JsonFormatter().format(record))
    assert output["event"] == "request_completed" and output["status"] == 200
    assert "timestamp" in output
