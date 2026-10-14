import os
from collections.abc import Iterator

import pytest
from fastapi.testclient import TestClient
from pydantic import SecretStr
from sqlalchemy.engine import make_url

from workboard.infrastructure.config import Settings
from workboard.infrastructure.models import Base
from workboard.main import create_app

TEST_SECRET = "test-only-signing-secret-with-at-least-32-characters"
PASSWORD = "CorrectHorseBattery42!"


@pytest.fixture
def client(tmp_path) -> Iterator[TestClient]:
    url = os.getenv("TEST_DATABASE_URL", f"sqlite:///{tmp_path / 'test.db'}")
    if not make_url(url).database.endswith(("_test", "_tests", "test.db")):
        raise RuntimeError(
            "TEST_DATABASE_URL must point to a dedicated *_test or *_tests database."
        )
    settings = Settings(jwt_secret=SecretStr(TEST_SECRET), database_url=url, _env_file=None)
    app = create_app(settings)
    engine = app.state.engine
    # Each test has a clean, disposable schema; never point this at an application database.
    Base.metadata.drop_all(engine)
    Base.metadata.create_all(engine)
    try:
        with TestClient(app) as connection:
            yield connection
    finally:
        Base.metadata.drop_all(engine)
        engine.dispose()


def register_and_login(client: TestClient, email: str = "owner@example.com") -> dict[str, str]:
    body = {"email": email, "password": PASSWORD}
    assert client.post("/api/v1/auth/register", json=body).status_code == 201
    response = client.post("/api/v1/auth/token", json=body)
    assert response.status_code == 200
    return {"Authorization": f"Bearer {response.json()['access_token']}"}


@pytest.fixture
def headers(client) -> dict[str, str]:
    return register_and_login(client)


@pytest.fixture
def project(client, headers) -> dict:
    response = client.post("/api/v1/projects", headers=headers, json={"name": "Launch"})
    assert response.status_code == 201
    return response.json()


@pytest.fixture
def task(client, headers, project) -> dict:
    response = client.post(
        f"/api/v1/projects/{project['id']}/tasks",
        headers=headers,
        json={"title": "Ship the API", "description": "Reviewed"},
    )
    assert response.status_code == 201
    return response.json()
