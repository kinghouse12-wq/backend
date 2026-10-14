# Contributing

Use Python 3.12–3.14 and uv. Run `python3 scripts/bootstrap.py`, then `uv sync --frozen`.

Run `make check`, `make test`, and the migration checks documented in the README. Persistence
changes need a reviewed Alembic migration with upgrade/downgrade verification. Set
`TEST_DATABASE_URL` only to a disposable `_test` or `_tests` database.

Keep dependencies flowing inward: domain rules, application use cases/transaction decisions,
infrastructure storage/security adapters, and API validation/serialization. Extend a port only
when a use case needs it. Test observable behavior, especially authorization, rollback, and races.

For API changes, run `uv run python scripts/export_openapi.py` and include `docs/openapi.json` in
the same change. Keep the demo/README commands executable. Never commit `.env`, databases, tokens,
or runtime logs. Describe the problem, resulting behavior, and validation in a pull request.
