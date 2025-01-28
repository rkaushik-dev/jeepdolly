"""Alembic environment.

Two things here are not boilerplate.

The star import of the models is load bearing. A table that has not been
imported is not on the metadata, and autogenerate will cheerfully write a
migration that drops it. The import is what stops that.

Batch mode is on for SQLite. SQLite cannot alter a column, so alembic has to
rebuild the table around the change, and it only does that when it is asked.
The office runs Postgres and the developers run SQLite, so both have to work
off the same migration file.
"""

from __future__ import annotations

from logging.config import fileConfig

from alembic import context
from sqlalchemy import engine_from_config, pool

from jeepdolly.persistence.base import Base
from jeepdolly.persistence.models import *  # noqa: F401,F403

config = context.config

if config.config_file_name is not None:
    # Without this, fileConfig switches off every logger that already exists,
    # including the office logger, and a service that runs a migration on the
    # way up comes up silent. It cost an afternoon to find the first time.
    fileConfig(config.config_file_name, disable_existing_loggers=False)

target_metadata = Base.metadata


def run_migrations_offline() -> None:
    """Write the SQL out rather than running it, which is how the server does it."""
    context.configure(
        url=config.get_main_option("sqlalchemy.url"),
        target_metadata=target_metadata,
        literal_binds=True,
        dialect_opts={"paramstyle": "named"},
        render_as_batch=True,
    )
    with context.begin_transaction():
        context.run_migrations()


def run_migrations_online() -> None:
    """Run against a live connection, which is how a developer does it."""
    connectable = engine_from_config(
        config.get_section(config.config_ini_section, {}),
        prefix="sqlalchemy.",
        poolclass=pool.NullPool,
    )
    with connectable.connect() as connection:
        context.configure(
            connection=connection,
            target_metadata=target_metadata,
            render_as_batch=connection.dialect.name == "sqlite",
        )
        with context.begin_transaction():
            context.run_migrations()


if context.is_offline_mode():
    run_migrations_offline()
else:
    run_migrations_online()
