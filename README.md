# Workboard API

[![CI](https://github.com/kinghouse12-wq/backend/actions/workflows/ci.yml/badge.svg)](https://github.com/kinghouse12-wq/backend/actions/workflows/ci.yml)
![Python](https://img.shields.io/badge/Python-3.12%E2%80%933.14-3776AB?logo=python&logoColor=white)
![Database](https://img.shields.io/badge/PostgreSQL-17-4169E1?logo=postgresql&logoColor=white)

**A layered project and task backend with ownership, explicit workflows, and safe concurrent updates.**

The interesting part is what happens when requests go wrong: another user's project stays private,
an invalid transition changes nothing, a stale edit cannot overwrite a newer one, and an audit
failure rolls back the task update. These guarantees are tested through the HTTP API and database.

## Review in five minutes

1. Read the architecture below and [the design decisions](docs/architecture.md).
2. Follow [`TaskService.update`](src/workboard/application/services.py) from authorization to commit.
3. Inspect the [atomic version check](src/workboard/infrastructure/repositories.py).
4. Read the [failure-path tests](tests/test_projects_tasks.py) and [concurrent-writer test](tests/test_postgres.py).
5. Run the demo against a real server; it exercises success, `409`, and `422` responses.

| Capability | Engineering detail |
| --- | --- |
| Authentication | Argon2id hashes; short-lived JWTs; fixed algorithm, issuer, and audience |
| Project ownership | All nested resources inherit ownership; unauthorized access returns `404` |
| Task workflow | Validated inputs, status filtering, bounded pagination, explicit transitions |
| Concurrent edits | Atomic compare-and-swap with `expected_version`; stale writes return `409` |
| Status history | Task/event writes share a transaction; history is paginated |
| Operations | Liveness, database readiness, JSON request logs, validated request IDs |
| Delivery | Alembic migrations, locked dependencies, lint/type/coverage gates, container CI |

## Architecture

```mermaid
flowchart LR
    Client[HTTP client] --> API[API routes and schemas]
    API --> APP[Application services]
    APP --> DOMAIN[Domain entities and workflow rules]
    APP --> PORTS[Repository and unit-of-work ports]
    INFRA[SQLAlchemy and security adapters] -. implements .-> PORTS
    INFRA --> DB[(PostgreSQL)]
    ROOT[Application factory] -. wires .-> API
    ROOT -. wires .-> INFRA
```

Domain/application layers import no framework or ORM; a test enforces this boundary. Services
decide when to commit. SQLAlchemy adapters implement the ports, and closing an uncommitted
request session rolls it back.

```text
src/workboard/
├── api/             # HTTP routes, contracts, dependency injection, errors
├── application/     # Use cases, ports, transaction decisions
├── domain/          # Immutable entities, workflow rules, errors
├── infrastructure/  # SQLAlchemy, settings, Argon2id/JWT, JSON logs
└── main.py          # Application factory and composition root
migrations/          # Versioned schema changes
tests/               # Domain, HTTP, failure-path, PostgreSQL concurrency tests
scripts/             # Secret bootstrap and executable HTTP demo
docs/                # Design decisions and generated OpenAPI contract
```

## Run with Docker and PostgreSQL

Prerequisites: Git, Python 3.12+ for the helper scripts, and Docker with Compose.

```sh
git clone https://github.com/kinghouse12-wq/backend.git
cd backend
python3 scripts/bootstrap.py
docker compose up --build -d
curl --fail http://localhost:8000/health/ready
python3 scripts/demo.py
```

If startup is still in progress, inspect `docker compose ps` or `docker compose logs api migrate`
and retry readiness. Compose starts PostgreSQL, runs migrations, then starts the API. Ports bind
to localhost; the database uses a named volume. The API runs as a non-root user.
To stop while preserving data: `docker compose down`.

Open **[Swagger UI](http://localhost:8000/docs)** or **[ReDoc](http://localhost:8000/redoc)**.
The contract is at `/openapi.json`, with a [generated copy in the repository](docs/openapi.json).
`bootstrap.py` generates random local credentials in a gitignored `.env`; it never overwrites
an existing file. `.env.example` contains configuration names and unusable placeholders.

## Fast local setup

Install [uv](https://docs.astral.sh/uv/getting-started/installation/) first.

```sh
python3 scripts/bootstrap.py
uv sync --frozen
uv run alembic upgrade head
uv run uvicorn workboard.main:create_app --factory --reload
```

This path uses SQLite for fast feedback. PostgreSQL is the deployment target and is tested in CI.
For your own PostgreSQL instance, set `DATABASE_URL` in `.env` to
`postgresql+psycopg://USER:PASSWORD@HOST:PORT/DATABASE` before applying migrations.

## Try it

```sh
curl -s http://localhost:8000/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"reviewer@example.com","password":"DemoPassword42!"}'
curl -s http://localhost:8000/api/v1/auth/token \
  -H 'Content-Type: application/json' \
  -d '{"email":"reviewer@example.com","password":"DemoPassword42!"}'
```

Paste `access_token` into Swagger's **Authorize** dialog or use `Authorization: Bearer <token>`.
Create a project, create a task, then update it using the version from the last read:

```http
PATCH /api/v1/projects/{project_id}/tasks/{task_id}
Authorization: Bearer <token>
Content-Type: application/json

{"status":"in_progress","expected_version":1}
```

The response has version `2`; another edit with version `1` gets `409`. Error responses include
`{error: {code, message}, request_id}`; validation errors also include sanitized field details.
The request ID matches the `X-Request-ID` response header.

```mermaid
stateDiagram-v2
    [*] --> todo
    todo --> in_progress
    todo --> cancelled
    in_progress --> todo
    in_progress --> done
    in_progress --> cancelled
    done --> in_progress: reopen
    cancelled --> todo: restore
```

Same-status updates are allowed without duplicate history events. Every successful edit increments
the version. `description: null` clears it; omitted fields keep their values. Deleting a project
cascades its tasks and history.

## Endpoints

Application paths use `/api/v1`. Lists return `{items, total, limit, offset}` with limits of
1–100 and offsets of 0–10,000. Projects/tasks sort by newest creation time and UUID; events by version.

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/auth/register`, `/auth/token` | Register / issue a token from JSON credentials |
| GET | `/auth/me` | Authenticated user |
| POST / GET | `/projects` | Create/list your projects |
| GET / DELETE | `/projects/{id}` | Read/delete your project |
| POST / GET | `/projects/{id}/tasks` | Create/list tasks; optional `status` filter |
| GET / PATCH | `/projects/{id}/tasks/{task_id}` | Read/update a task |
| GET | `/projects/{id}/tasks/{task_id}/events` | Status history |
| GET | `/health/live`, `/health/ready` | Process/database probes; outside `/api/v1` |

## Verify

```sh
uv run ruff check .
uv run ruff format --check .
uv run mypy
uv run pytest
uv run alembic check
```

Coverage must be **at least 85% including branches**. SQLite tests use isolated databases; the
competing-writer test skips without PostgreSQL. To exercise it, use a **disposable** `_test` or
`_tests` database (the suite resets its schema):

```sh
TEST_DATABASE_URL=postgresql+psycopg://USER:PASSWORD@localhost:5432/workboard_test uv run pytest
```

CI tests Python 3.12/3.14 on PostgreSQL 17, checks migration upgrade/downgrade and schema drift,
verifies the committed OpenAPI contract, then builds/starts Docker and runs the real HTTP demo.
GitHub Actions are pinned to immutable commits.

## Scope

A focused, production-minded portfolio service with documented tradeoffs—not a claim of production
deployment. [Design notes](docs/architecture.md) explain what comes next: ingress/TLS, gateway rate
limits, account recovery/revocation, and backups. One service and one database are sufficient for
these workflows; queues, Redis, and microservices need a measured requirement first.

Implementation references: [FastAPI security](https://fastapi.tiangolo.com/tutorial/security/oauth2-jwt/),
[SQLAlchemy transactions](https://docs.sqlalchemy.org/en/20/orm/session_basics.html),
[uv CI integration](https://docs.astral.sh/uv/guides/integration/github/).
