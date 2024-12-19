"""The migration, checked against the models it is supposed to build.

This is the test that catches the mistake everybody makes: a table added to
the models and not to a migration. It builds a database twice, once from the
models and once from the migration, and compares them.
"""

from pathlib import Path

import pytest
from alembic import command
from alembic.config import Config
from sqlalchemy import create_engine, inspect

from jeepdolly.persistence.base import Base, create_all
from jeepdolly.persistence.models import *  # noqa: F401,F403

ROOT = Path(__file__).resolve().parents[1]

#: Tables that exist only because a test module defined a model.
NOT_SHIPPED = {"sample_measures"}


def alembic_config(url: str) -> Config:
    config = Config(str(ROOT / "alembic.ini"))
    config.set_main_option("script_location", str(ROOT / "migrations"))
    config.set_main_option("sqlalchemy.url", url)
    return config


@pytest.fixture
def migrated(tmp_path):
    """A database built by running every migration."""
    url = f"sqlite+pysqlite:///{tmp_path / 'migrated.sqlite3'}"
    command.upgrade(alembic_config(url), "head")
    return create_engine(url, future=True)


@pytest.fixture
def declared(tmp_path):
    """A database built straight from the models."""
    url = f"sqlite+pysqlite:///{tmp_path / 'declared.sqlite3'}"
    engine = create_engine(url, future=True)
    create_all(engine)
    return engine


def shipped(names):
    return {one for one in names if one not in NOT_SHIPPED}


def test_the_migration_builds_every_table_the_models_declare(migrated, declared):
    from_migration = shipped(inspect(migrated).get_table_names())
    from_models = shipped(inspect(declared).get_table_names())
    assert from_migration - {"alembic_version"} == from_models
    assert "permits" in from_migration


def test_every_table_has_the_same_columns_in_both(migrated, declared):
    walked = 0
    for table in sorted(shipped(inspect(declared).get_table_names())):
        from_migration = {one["name"] for one in inspect(migrated).get_columns(table)}
        from_models = {one["name"] for one in inspect(declared).get_columns(table)}
        assert from_migration == from_models, table
        walked += 1
    assert walked > 15


def test_the_indexes_the_reports_need_are_in_the_migration(migrated):
    found = {one["name"] for one in inspect(migrated).get_indexes("permits")}
    assert "ix_permits_valid_from" in found
    assert "ix_permits_usdot" in found


def test_the_foreign_keys_are_named_by_the_convention(migrated):
    keys = inspect(migrated).get_foreign_keys("permits")
    names = {one["name"] for one in keys}
    assert "fk_permits_usdot_carriers" in names
    assert "fk_permits_application_applications" in names


def test_the_migration_reverses_all_the_way_back_to_nothing(tmp_path):
    url = f"sqlite+pysqlite:///{tmp_path / 'reversed.sqlite3'}"
    config = alembic_config(url)
    command.upgrade(config, "head")
    command.downgrade(config, "base")
    engine = create_engine(url, future=True)
    left = shipped(inspect(engine).get_table_names())
    assert left == {"alembic_version"} or left == set()


def test_the_metadata_carries_the_naming_convention_that_names_them():
    assert Base.metadata.naming_convention["fk"].startswith("fk_%(table_name)s")
    assert Base.metadata.naming_convention["ck"] == "ck_%(table_name)s_%(constraint_name)s"
