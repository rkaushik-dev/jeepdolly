"""Filing a citation, which is four steps a clerk used to do by hand.

A ticket arrives in the post. The clerk finds the carrier, counts what else
that carrier has had in the last year, works out the money off the schedule
that was in force on the day the ticket was written, and files it. Every one
of those has been got wrong at least once, and the count is the one that gets
got wrong most, because it wants a decision about dismissed tickets that is
not written down anywhere except in one supervisor's head.

So it is here, once. The order is not negotiable and the day is a parameter,
the same rule the counter service follows.

Two things this deliberately does not do.

It does not take money. A citation is paid at the till like everything else,
and the till is a different record with a different signature. What this does
is move the citation to paid when it is told the money arrived.

It does not suspend a carrier. Enough citations in a year is grounds for a
suspension and the supervisor makes that decision on the paper, because a
system that suspended a carrier automatically would suspend one over a ticket
that was going to be dismissed the following week.
"""

from __future__ import annotations

from datetime import date

from ..enforcement.citation import Citation, CitationState
from ..enforcement.penalty import Offence, PenaltySchedule, assess, standard_schedule
from ..errors import Impossible
from ..measure import Pounds
from ..permits.numbering import Series
from ..persistence.unitofwork import UnitOfWork
from . import audit

#: The series citations are numbered in, off the same counter as everything else.
CITATION_SERIES = Series.CITATION


def schedule_on(day: date) -> PenaltySchedule:
    """The penalty schedule in force on a day.

    There is one schedule and it is in the code, the same way the statutory
    tables were before they moved to rows. When the legislature amends the
    penalties this becomes a lookup on a table and the callers do not change,
    which is why they all ask for it by day rather than reaching for the
    standard one themselves.
    """
    return standard_schedule()


def file_citation(
    work: UnitOfWork,
    number: str,
    usdot: int,
    officer: str,
    offence: Offence,
    place: str,
    written: date,
    filed: date,
    who: str,
    over: Pounds | None = None,
    permit: str | None = None,
    note: str = "",
) -> Citation:
    """Price a roadside ticket against the carrier's year and put it on file."""
    if filed < written:
        raise Impossible(f"{number} cannot be filed before it was written")
    carrier = work.carriers.get(usdot, written)
    schedule = schedule_on(written)
    priors = work.citations.priors(usdot, written)
    citation = Citation(
        number=number,
        written=written,
        filed=filed,
        officer=officer,
        usdot=usdot,
        carrier_name=carrier.name,
        offence=offence,
        place=place,
        assessed=assess(schedule, offence, over, priors),
        permit=permit,
        over=over if offence.by_weight else None,
        note=note,
    )
    work.citations.add(citation, schedule.effective)
    audit.record(
        work.db,
        "citation.filed",
        who,
        "citation",
        number,
        detail=f"{offence} {citation.amount}",
    )
    return citation


def contest(work: UnitOfWork, number: str, on: date, reason: str, who: str) -> Citation:
    """The carrier says no, in writing, inside the window."""
    citation = work.citations.get(number)
    citation.contest(on, reason)
    work.citations.save_state(citation)
    audit.record(work.db, "citation.contested", who, "citation", number, detail=reason)
    return citation


def uphold(work: UnitOfWork, number: str, on: date, who: str, note: str = "") -> Citation:
    """The hearing went the office's way."""
    citation = work.citations.get(number)
    citation.uphold(on, note)
    work.citations.save_state(citation)
    audit.record(work.db, "citation.upheld", who, "citation", number, detail=note)
    return citation


def dismiss(work: UnitOfWork, number: str, on: date, reason: str, who: str) -> Citation:
    """The hearing went the carrier's way, and it stops being a prior offence."""
    citation = work.citations.get(number)
    citation.dismiss(on, reason)
    work.citations.save_state(citation, reason)
    audit.record(work.db, "citation.dismissed", who, "citation", number, detail=reason)
    return citation


def pay(work: UnitOfWork, number: str, on: date, who: str, reference: str = "") -> Citation:
    """Money arrived at the till and this is the paper catching up."""
    citation = work.citations.get(number)
    citation.pay(on, reference)
    work.citations.save_state(citation)
    audit.record(work.db, "citation.paid", who, "citation", number, detail=reference)
    return citation


def void(work: UnitOfWork, number: str, on: date, reason: str, who: str) -> Citation:
    """The office's own mistake, killed by a supervisor with a reason on it."""
    citation = work.citations.get(number)
    citation.void(on, reason)
    work.citations.save_state(citation, reason)
    audit.record(work.db, "citation.voided", who, "citation", number, detail=reason)
    return citation


def next_number(work: UnitOfWork, on: date) -> str:
    """A citation number off the same counter every other document uses."""
    return str(work.counter.number(on, CITATION_SERIES))


def owed_by(work: UnitOfWork, usdot: int) -> int:
    """How many cents a carrier still owes on citations, for the counter screen."""
    return sum(
        one.amount.cents
        for one in work.citations.for_carrier(usdot)
        if CitationState(one.state).owing
    )
