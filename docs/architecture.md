# Design decisions

## Modular monolith and dependency inversion

The API translates HTTP into use cases. Services depend on repository, unit-of-work, password, and
token protocols. The domain owns immutable entities and allowed transitions. Infrastructure
implements the protocols; `create_app` wires everything together.

The abstractions correspond to actual boundaries. There is no generic base repository or separate
interface for every class. An architecture test rejects outer-layer imports in domain/application.
ORM rows stay inside the database adapter; HTTP contracts stay inside the API.

## PostgreSQL deployment, SQLite feedback

The schema uses UUIDs, cascading foreign keys, status/version constraints, and query-aligned
composite indexes. All timestamps are UTC; the adapter normalizes SQLite's naive results to UTC.
Alembic is the only runtime schema-management mechanism. Application startup never calls
`create_all`. Tests create disposable schemas, while CI independently checks migration upgrade,
downgrade, and drift against PostgreSQL.

Offset pagination is simple and bounded. Deep traversal would justify keyset pagination; exact
count/page consistency during concurrent writes would require an appropriate snapshot.

## Concurrency and transactions

Every task starts at version 1. The service checks ownership and rules, then the adapter executes:

```sql
UPDATE tasks
SET title = :title, status = :status, version = :next_version, updated_at = :now
WHERE id = :id AND project_id = :project_id AND version = :expected_version
RETURNING id;
```

A zero-row result becomes `409`. Checking only in Python would still allow competing transactions
to overwrite each other. The PostgreSQL test uses two sessions and a barrier so both writers read
the same version before competing; exactly one update can succeed.

The use case commits the task and its status event together. An injected audit failure verifies
that the preceding SQL update rolls back. History covers creation/status changes, not every text
edit. It is project history rather than a compliance ledger; project deletion cascades it.

## Authorization before nested access

Every task/event use case resolves the owned project, then the task within that project. A task
UUID cannot be used through an unrelated project. Missing and unauthorized resources both return
`404` to avoid revealing another user's data. Tests use a second account for read/create/update/
history/delete paths.

## Authentication boundaries

Passwords use Argon2id; unknown-user login attempts verify a dummy hash. JWTs expire after 15 minutes
by default (maximum 60), require subject/issued-at/expiry/issuer/audience, and allow only HS256.
Credentials are JSON: this is bearer-token authentication, not an OAuth authorization server.
Passwords retain whitespace and must be 12–128 characters at registration and login.

Signing credentials come from the environment. The bootstrap creates random local values rather
than shipping a shared key. Requests log only method/path/status/duration/request ID, never tokens
or credentials. Validation errors omit raw inputs, and unexpected errors return a generic message.

There is no refresh-token, logout-revocation, email-verification, password-reset, or distributed
rate-limiting system. Before public hosting, add HTTPS, login/registration gateway rate limits,
secret management, suitable host/CORS settings, and recovery/revocation flows. Argon2 consumes
memory; bound concurrent authentication requests at the ingress.

## Synchronous I/O is intentional

Routes performing SQL/password work use `def`, so FastAPI executes them in its thread pool. This
matches the synchronous PostgreSQL driver. The async request middleware does no database work.
An async rewrite would change adapters/transactions; it would not accelerate password hashing.

## Operations and delivery

Liveness is independent of database availability. Readiness executes `SELECT 1` and returns `503`
on connection failure; it checks connectivity, not schema version. Migrations are a startup
prerequisite enforced by Compose. The app owns/disposes its engine; each request owns one session.
Services decide when to commit, and session closure rolls back pending work.

The container uses a locked dependency set, a non-root user, a readiness health check, and no
bundled secrets. Compose binds localhost ports and persists PostgreSQL in a named volume. Public
deployment still requires ingress/TLS, managed backups, a migration rollout policy, error
monitoring, and traffic-appropriate resource limits. Performance/availability are not claimed
without measurement.
