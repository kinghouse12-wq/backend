# Engineering decisions

## Layers without unnecessary machinery

Java records form the immutable domain and HTTP responses. Business transitions are framework-free;
Spring annotations on application services supply dependency injection and transaction boundaries.
The services depend on repository/security ports, implemented by infrastructure. JPA entities never
leave the adapter. ArchUnit catches dependencies that reverse these boundaries.

There is one service and one relational database. There is no generic base service, reflection mapper,
Lombok dependency, queue, distributed cache, or microservice split without a concrete requirement.

## Atomic concurrency control

Task updates use a single JPQL bulk statement whose SQL has `WHERE version = expectedVersion`, plus
`version = version + 1`. A zero-row update becomes `409`. The `@Version` column also supports ordinary
JPA optimistic updates; the bulk statement explicitly increments it because bulk JPQL bypasses automatic
entity dirty checking. The persistence context is cleared after the update to avoid stale entities.

Checking only in Java would allow two transactions to overwrite one another. The PostgreSQL test uses
two transactions and a barrier so both read the same version before racing. Exactly one writer wins.
Clients must fetch the latest version before retrying a conflict.

## Transactional history and authorization

The service method's `@Transactional` boundary encloses both the task update and the status event.
A test injects an audit-write failure and verifies that the task rolls back. History covers creation
and status changes, not every text edit; it is project history rather than a compliance ledger.
Database cascades deliberately remove history when its project is deleted.

Every nested use case resolves the owned project before the task. A task cannot be addressed through
another project; inaccessible and missing resources both return `404`. Ownership comes from the verified
JWT subject, never a request body field.

## Persistence and operations

Flyway owns schema evolution. Hibernate validates mappings and Open Session in View is disabled.
UUIDs, cascading foreign keys, status/version checks, and query-aligned indexes are in the migration.
H2 offers fast local tests; CI uses PostgreSQL for database-specific behavior and real concurrency.
Offset/page pagination is bounded; deep traversal would justify keyset pagination and stronger snapshot
semantics if count/page consistency during concurrent inserts becomes a requirement.

Actuator readiness includes the database; liveness is independent. ECS JSON logs include validated
request IDs and method/path/status/duration, without raw credentials, token bodies, or query strings.
Containers run as a non-root user, ports bind to localhost, and credentials come from the environment.

## Security boundaries

Spring Security's resource-server support verifies JWT signatures using an HS256 allowlist, issuer,
audience, required claims, expiry, and a 30-second clock tolerance. JSON credentials issue short-lived
tokens; this API is not an OAuth authorization server. BCrypt uses cost 12 and enforces its 72-byte
input limit. Unknown-user login attempts verify a dummy hash. Validation payloads never echo passwords.

CSRF is disabled because authentication uses explicit bearer headers and no ambient session cookies;
sessions are stateless and CORS origins are denied by default. There are no refresh tokens, distributed
rate limits, email verification, password reset, or logout revocation. Public hosting needs ingress/TLS,
gateway rate limiting, secret management, recovery/revocation design, and tested backups. Heavy password
hashing should be bounded at the ingress. Operational metrics exist through Actuator/Micrometer; the
HTTP exposure deliberately stays limited to health/info.
