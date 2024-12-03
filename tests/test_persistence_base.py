"""The database floor: the column types, the naming, and the memory engine."""

from datetime import date

import pytest
from sqlalchemy import Date, String, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Mapped, mapped_column

from jeepdolly.domain.highway import HighwayClass
from jeepdolly.measure import Inches, Miles, Pounds
from jeepdolly.money import Dollars
from jeepdolly.persistence.base import (
    Base,
    CentsType,
    EnumValue,
    InchesType,
    MilesType,
    PoundsType,
    as_date,
    create_all,
    memory_engine,
    session_factory,
)


class Sample(Base):
    """A table that exists only to prove the column types round trip."""

    __tablename__ = "sample_measures"

    ident: Mapped[str] = mapped_column(String(16), primary_key=True)
    weight: Mapped[Pounds] = mapped_column(PoundsType)
    size: Mapped[Inches] = mapped_column(InchesType)
    distance: Mapped[Miles] = mapped_column(MilesType)
    fee: Mapped[Dollars] = mapped_column(CentsType)
    road: Mapped[HighwayClass] = mapped_column(EnumValue(HighwayClass))
    day: Mapped[date] = mapped_column(Date)
    spare: Mapped[Pounds | None] = mapped_column(PoundsType, nullable=True)
    spare_road: Mapped[HighwayClass | None] = mapped_column(EnumValue(HighwayClass), nullable=True)


@pytest.fixture
def session():
    engine = memory_engine()
    create_all(engine)
    with session_factory(engine)() as opened:
        yield opened


def a_row(ident="one"):
    return Sample(
        ident=ident,
        weight=Pounds(132400),
        size=Inches.of(14, 6),
        distance=Miles.of(35, 20),
        fee=Dollars.of(188, 72),
        road=HighwayClass.STATE_AID,
        day=date(2025, 4, 8),
    )


def test_every_measure_comes_back_as_the_type_it_went_in_as(session):
    session.add(a_row())
    session.commit()
    session.expunge_all()
    found = session.scalars(select(Sample)).one()
    assert found.weight == Pounds(132400)
    assert found.size == Inches(174)
    assert found.distance == Miles(3520)
    assert found.fee == Dollars(18872)
    assert found.road is HighwayClass.STATE_AID
    assert found.day == date(2025, 4, 8)


def test_the_measures_are_stored_as_whole_numbers_and_not_as_text(session):
    session.add(a_row())
    session.commit()
    row = session.connection().exec_driver_sql("select * from sample_measures").mappings().one()
    assert row["weight"] == 132400
    assert row["size"] == 174
    assert row["distance"] == 3520
    assert row["fee"] == 18872
    assert row["road"] == "state aid"


def test_a_null_measure_comes_back_as_none_rather_than_a_zero(session):
    session.add(a_row(ident="two"))
    session.commit()
    session.expunge_all()
    found = session.scalars(select(Sample).where(Sample.ident == "two")).one()
    assert found.spare is None
    assert found.spare_road is None
    assert found.weight == Pounds(132400)


def test_the_primary_key_is_named_after_the_convention(session):
    table = Base.metadata.tables["sample_measures"]
    assert table.primary_key.name == "pk_sample_measures"


def test_the_memory_engine_keeps_one_database_across_connections():
    engine = memory_engine()
    create_all(engine)
    with session_factory(engine)() as first:
        first.add(a_row())
        first.commit()
    with session_factory(engine)() as second:
        assert second.scalars(select(Sample)).one().ident == "one"


def test_two_memory_engines_do_not_share_anything():
    first = memory_engine()
    create_all(first)
    with session_factory(first)() as opened:
        opened.add(a_row())
        opened.commit()
    second = memory_engine()
    create_all(second)
    with session_factory(second)() as opened:
        assert opened.scalars(select(Sample)).all() == []


def test_the_same_key_twice_is_refused_by_the_database(session):
    session.add(a_row())
    session.commit()
    session.add(a_row())
    with pytest.raises(IntegrityError):
        session.commit()


def test_a_date_is_read_from_a_string_when_it_arrives_as_one():
    assert as_date("2025-04-08") == date(2025, 4, 8)
    assert as_date(date(2025, 4, 8)) == date(2025, 4, 8)
