"""The database floor: one base, named constraints, and honest columns.

Three decisions here and all three are the sort that are expensive to change
later, so they are made once and written down.

**Every constraint is named.** SQLite and Postgres invent different names for
an unnamed unique constraint, and alembic then writes a migration that runs on
a laptop and fails on the server. The convention below makes the name a
function of the columns, so it is the same everywhere and a migration that
drops a constraint can say which one.

**Nothing is stored in a type that loses information.** A weight is a whole
number of pounds, a size is a whole number of inches, a distance is whole
hundredths of a mile, money is whole cents. No column in this schema is a
float, and the type decorators below are the only way those values reach the
database, so nobody can add one by accident.

**Enumerations are stored as their short string value.** Not as an ordinal,
because rows have to be readable in a console at two in the morning, and not as
a database enum type, because adding a class of road should not be a migration
on Postgres.

The in memory engine is here rather than in the tests, because the same trick
is wanted by the command line for a dry run: a database that behaves exactly
like the real one and vanishes afterwards. It needs the static pool, or every
connection gets its own empty database and nothing anybody writes is ever seen
again.
"""

from __future__ import annotations

from datetime import date
from typing import Any

from sqlalchemy import Integer, MetaData, String, TypeDecorator, create_engine, event
from sqlalchemy.engine import Engine
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker
from sqlalchemy.pool import StaticPool

from ..measure import Inches, Miles, Pounds
from ..money import Dollars

NAMING = {
    "ix": "ix_%(table_name)s_%(column_0_N_name)s",
    "uq": "uq_%(table_name)s_%(column_0_N_name)s",
    "ck": "ck_%(table_name)s_%(constraint_name)s",
    "fk": "fk_%(table_name)s_%(column_0_N_name)s_%(referred_table_name)s",
    "pk": "pk_%(table_name)s",
}

#: Long enough for a carrier name, short enough that nobody pastes an essay.
NAME_LENGTH = 120

#: Codes, idents and references.
CODE_LENGTH = 32

#: A condition, a note, a refusal reason.
TEXT_LENGTH = 500


class Base(DeclarativeBase):
    metadata = MetaData(naming_convention=NAMING)


class PoundsType(TypeDecorator[Pounds]):
    """A weight, stored as a whole number of pounds."""

    impl = Integer
    cache_ok = True

    def process_bind_param(self, value: Pounds | None, dialect: Any) -> int | None:
        return None if value is None else value.value

    def process_result_value(self, value: int | None, dialect: Any) -> Pounds | None:
        return None if value is None else Pounds(value)


class InchesType(TypeDecorator[Inches]):
    """A size, stored as whole inches."""

    impl = Integer
    cache_ok = True

    def process_bind_param(self, value: Inches | None, dialect: Any) -> int | None:
        return None if value is None else value.value

    def process_result_value(self, value: int | None, dialect: Any) -> Inches | None:
        return None if value is None else Inches(value)


class MilesType(TypeDecorator[Miles]):
    """A distance, stored as whole hundredths of a mile."""

    impl = Integer
    cache_ok = True

    def process_bind_param(self, value: Miles | None, dialect: Any) -> int | None:
        return None if value is None else value.hundredths

    def process_result_value(self, value: int | None, dialect: Any) -> Miles | None:
        return None if value is None else Miles(value)


class CentsType(TypeDecorator[Dollars]):
    """Money, stored as whole cents."""

    impl = Integer
    cache_ok = True

    def process_bind_param(self, value: Dollars | None, dialect: Any) -> int | None:
        return None if value is None else value.cents

    def process_result_value(self, value: int | None, dialect: Any) -> Dollars | None:
        return None if value is None else Dollars(value)


class EnumValue(TypeDecorator[Any]):
    """An enumeration, stored as its short string value."""

    impl = String(32)
    cache_ok = True

    def __init__(self, enum_type: Any, *args: Any, **kw: Any) -> None:
        self.enum_type = enum_type
        super().__init__(*args, **kw)

    def process_bind_param(self, value: Any, dialect: Any) -> str | None:
        return None if value is None else str(value.value)

    def process_result_value(self, value: Any, dialect: Any) -> Any:
        return None if value is None else self.enum_type(value)


def engine_for(url: str, echo: bool = False) -> Engine:
    """A connected engine, with SQLite told to enforce foreign keys.

    SQLite does not enforce them unless it is asked, once per connection, and
    a suite that skips this passes against constraints the server refuses.
    """
    engine = create_engine(url, echo=echo, future=True)
    _keys_on(engine)
    return engine


def memory_engine(echo: bool = False) -> Engine:
    """A private in memory database that survives more than one connection."""
    engine = create_engine(
        "sqlite+pysqlite:///:memory:",
        echo=echo,
        future=True,
        poolclass=StaticPool,
        connect_args={"check_same_thread": False},
    )
    _keys_on(engine)
    return engine


def _keys_on(engine: Engine) -> None:
    if engine.dialect.name != "sqlite":
        return

    @event.listens_for(engine, "connect")
    def _pragma(connection: Any, record: Any) -> None:  # pragma: no cover - driver callback
        cursor = connection.cursor()
        cursor.execute("PRAGMA foreign_keys=ON")
        cursor.close()


def session_factory(engine: Engine) -> sessionmaker[Session]:
    return sessionmaker(bind=engine, expire_on_commit=False, future=True)


def create_all(engine: Engine) -> None:
    Base.metadata.create_all(engine)


def drop_all(engine: Engine) -> None:
    Base.metadata.drop_all(engine)


def as_date(value: date | str) -> date:
    """Dates arrive from JSON as strings often enough to be worth one helper."""
    if isinstance(value, date):
        return value
    return date.fromisoformat(value)
