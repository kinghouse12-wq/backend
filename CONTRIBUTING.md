# Contributing

Use Java 21 and the Maven wrapper. Run `./mvnw spotless:apply`, then `./mvnw spotless:check verify`.

Keep HTTP contracts in `api`, orchestration/transactions in `application`, framework-free rules in
`domain`, and persistence/security details in `infrastructure`. Controllers must not call JPA directly.
Add a new versioned Flyway migration for schema changes; never edit an applied migration.

Test authorization, invalid input, stale writes, and rollback—not just successful CRUD. PostgreSQL
integration tests must target a disposable database; they remove application data. Update Swagger
annotations, the HTTP demo, and documentation alongside API changes. Never commit `.env` or credentials.
