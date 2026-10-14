from sqlalchemy import Engine, create_engine, event
from sqlalchemy.pool import StaticPool


def build_engine(url: str) -> Engine:
    if url.startswith("sqlite"):
        kwargs = {"poolclass": StaticPool} if ":memory:" in url else {}
        engine = create_engine(url, connect_args={"check_same_thread": False}, **kwargs)

        @event.listens_for(engine, "connect")
        def foreign_keys(connection: object, _: object) -> None:
            # DBAPI types vary by driver; this adapter is SQLite-only.
            connection.execute("PRAGMA foreign_keys=ON")  # type: ignore[attr-defined]

        return engine
    return create_engine(url, pool_pre_ping=True, connect_args={"connect_timeout": 5})
