from datetime import timedelta
from uuid import uuid4

import jwt
import pytest
from conftest import PASSWORD, TEST_SECRET
from sqlalchemy import select

from workboard.domain.entities import utc_now
from workboard.infrastructure.models import UserRow


def test_registration_hashes_password_and_normalizes_email(client):
    response = client.post(
        "/api/v1/auth/register",
        json={
            "email": "Owner@EXAMPLE.COM",
            "password": PASSWORD,
        },
    )
    assert response.status_code == 201
    assert response.json()["email"] == "owner@example.com"
    assert "password" not in response.text
    with client.app.state.session_factory() as session:
        row = session.scalar(select(UserRow))
        assert row.password_hash.startswith("$argon2id$")
        assert PASSWORD not in row.password_hash


def test_duplicate_email_is_conflict(client, headers):
    response = client.post(
        "/api/v1/auth/register",
        json={
            "email": "OWNER@example.com",
            "password": PASSWORD,
        },
    )
    assert response.status_code == 409
    assert response.json()["error"]["code"] == "conflict"


def test_login_and_me(client, headers):
    response = client.get("/api/v1/auth/me", headers=headers)
    assert response.status_code == 200
    assert response.json()["email"] == "owner@example.com"


@pytest.mark.parametrize(
    "email,password",
    [
        ("owner@example.com", "WrongPassword42!"),
        ("absent@example.com", PASSWORD),
    ],
)
def test_login_does_not_disclose_existing_account(client, headers, email, password):
    response = client.post("/api/v1/auth/token", json={"email": email, "password": password})
    assert response.status_code == 401
    assert response.json()["error"] == {
        "code": "invalid_credentials",
        "message": "Invalid email or password.",
    }
    assert response.headers["www-authenticate"] == "Bearer"


@pytest.mark.parametrize("authorization", [None, "Basic abc", "Bearer garbage"])
def test_invalid_credentials_are_rejected(client, authorization):
    headers = {"Authorization": authorization} if authorization else {}
    response = client.get("/api/v1/projects", headers=headers)
    assert response.status_code == 401


@pytest.mark.parametrize(
    "mutation", ["expired", "audience", "issuer", "subject", "missing", "user"]
)
def test_bad_jwt_claims_are_rejected(client, headers, mutation):
    user = client.get("/api/v1/auth/me", headers=headers).json()
    now = utc_now()
    claims = {
        "sub": user["id"],
        "iat": now,
        "exp": now + timedelta(minutes=5),
        "iss": "workboard-api",
        "aud": "workboard-client",
    }
    if mutation == "expired":
        claims["exp"] = now - timedelta(seconds=1)
    elif mutation == "audience":
        claims["aud"] = "another-service"
    elif mutation == "issuer":
        claims["iss"] = "another-issuer"
    elif mutation == "subject":
        claims["sub"] = "not-a-uuid"
    elif mutation == "missing":
        del claims["exp"]
    else:
        claims["sub"] = str(uuid4())
    token = jwt.encode(claims, TEST_SECRET, algorithm="HS256")
    assert (
        client.get("/api/v1/projects", headers={"Authorization": f"Bearer {token}"}).status_code
        == 401
    )


def test_wrong_signing_key_and_algorithm_are_rejected(client, headers):
    user = client.get("/api/v1/auth/me", headers=headers).json()
    now = utc_now()
    claims = {
        "sub": user["id"],
        "iat": now,
        "exp": now + timedelta(minutes=5),
        "iss": "workboard-api",
        "aud": "workboard-client",
    }
    for algorithm, key in [("HS256", TEST_SECRET + "wrong"), ("HS384", TEST_SECRET)]:
        token = jwt.encode(claims, key, algorithm=algorithm)
        assert (
            client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {token}"}).status_code
            == 401
        )


def test_validation_does_not_echo_credentials(client):
    secret = "shortSECRET"
    response = client.post("/api/v1/auth/register", json={"email": "bad", "password": secret})
    assert response.status_code == 422
    assert secret not in response.text
    assert response.json()["error"]["details"]


def test_password_whitespace_is_preserved(client):
    password = "  CorrectHorseBattery42!  "
    body = {"email": "spaces@example.com", "password": password}
    assert client.post("/api/v1/auth/register", json=body).status_code == 201
    assert client.post("/api/v1/auth/token", json=body).status_code == 200
    body["password"] = password.strip()
    assert client.post("/api/v1/auth/token", json=body).status_code == 401
