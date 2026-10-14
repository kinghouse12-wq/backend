.PHONY: install run migrate check test demo up down

install:
	uv sync --frozen

run:
	uv run uvicorn workboard.main:create_app --factory --reload

migrate:
	uv run alembic upgrade head

check:
	uv run ruff check .
	uv run ruff format --check .
	uv run mypy

test:
	uv run pytest

demo:
	python3 scripts/demo.py

up:
	docker compose up --build -d

down:
	docker compose down
