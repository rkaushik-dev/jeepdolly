"""Pilot car commands, which exist because of the renewal run.

Once a quarter somebody sits down with the register and rings everybody whose
card runs out in the next ninety days. That was a screen with no print button,
so it was done by reading the screen onto a pad, and the pad is how two people
were missed in the spring.

So the renewal list is a command. It prints one card to a line with the
telephone number of the company on it, and it goes straight into a mail merge
or straight onto a clipboard.

The rest of the group is here because once the office had one pilot car
command they wanted the others in the same place. Certifying from the command
line is used exactly once a month, on the morning after the examination, when
there are eleven new cards to put on and doing that through a form eleven
times is how a wrong expiry date gets typed.
"""

from __future__ import annotations

import sys
from datetime import date, datetime, timedelta

import click

from ..errors import Unreadable
from ..escorts.register import Company as PilotCompany
from ..escorts.register import Endorsement, EscortRegister, Operator
from ..persistence.books import escorts_from
from ..persistence.unitofwork import UnitOfWork
from .main import Console, main, say

#: How far ahead the renewal run looks when nobody says otherwise.
RENEWAL_DAYS = 90


@main.group()
def pilots() -> None:
    """Pilot car companies, their cards, and the renewal run."""


@pilots.command("companies")
@click.option("--suspended", is_flag=True, help="Only the ones that are off.")
@click.pass_obj
def companies(console: Console, suspended: bool) -> None:
    """Every pilot car company, with how many cards it holds."""
    with console.work() as work:
        for row in work.pilots.companies():
            if suspended and not row.suspended:
                continue
            mark = "suspended" if row.suspended else ""
            say(row.ident, row.name, row.town, row.telephone, len(row.cards), mark)


@pilots.command("cards")
@click.option("--company", default=None, help="One company rather than all of them.")
@click.option("--day", type=click.DateTime(["%Y-%m-%d"]), default=None)
@click.pass_obj
def cards(console: Console, company: str | None, day: datetime | None) -> None:
    """Every card, with what it is good for on a day."""
    when = day.date() if day else date.today()
    with console.work() as work:
        register = _register(work)
        for row in work.pilots.cards(company):
            standing = register.standing(row.card, when)
            marks = ",".join(sorted(one.value for one in row.to_domain().endorsements))
            say(row.card, row.name, row.company_ident, row.expires.isoformat(), standing, marks)


@pilots.command("expiring")
@click.option("--day", type=click.DateTime(["%Y-%m-%d"]), default=None)
@click.option("--within", type=int, default=RENEWAL_DAYS, help="Days ahead to look.")
@click.pass_obj
def expiring(console: Console, day: datetime | None, within: int) -> None:
    """The renewal run: who to ring, soonest first, with the number."""
    when = day.date() if day else date.today()
    with console.work() as work:
        found = work.pilots.expiring(when, when + timedelta(days=within))
        for row in found:
            company = work.pilots.company_row(row.company_ident)
            days = (row.expires - when).days
            say(row.card, row.name, company.name, company.telephone, row.expires.isoformat(), days)
    if not found:
        say("nothing runs out in that window")


@pilots.command("company")
@click.argument("ident")
@click.argument("name")
@click.option("--town", default="")
@click.option("--telephone", default="")
@click.pass_obj
def company(console: Console, ident: str, name: str, town: str, telephone: str) -> None:
    """Put a pilot car company on the register."""
    with console.work() as work:
        work.pilots.add_company(PilotCompany(ident, name, town, telephone))
        work.commit()
    say("filed", ident, name)


@pilots.command("certify")
@click.argument("card")
@click.argument("name")
@click.argument("company")
@click.option("--issued", type=click.DateTime(["%Y-%m-%d"]), required=True)
@click.option("--expires", type=click.DateTime(["%Y-%m-%d"]), required=True)
@click.option("--endorsement", "endorsements", multiple=True, help="May be given more than once.")
@click.pass_obj
def certify(
    console: Console,
    card: str,
    name: str,
    company: str,
    issued: datetime,
    expires: datetime,
    endorsements: tuple[str, ...],
) -> None:
    """Issue a card after the examination. A renewal comes through here too."""
    marks = frozenset(_endorsement(one) for one in endorsements)
    operator = Operator(card, name, company, issued.date(), expires.date(), marks)
    with console.work() as work:
        work.pilots.certify(operator)
        work.commit()
    say("certified", card, name, company, expires.date().isoformat())


@pilots.command("suspend")
@click.argument("card")
@click.option("--lift", is_flag=True, help="Let them back rather than stop them.")
@click.pass_obj
def suspend(console: Console, card: str, lift: bool) -> None:
    """Stop an operator working, or let them back."""
    with console.work() as work:
        row = work.pilots.suspend_card(card, not lift)
        work.commit()
    say("lifted" if lift else "suspended", card, row.name)


@pilots.command("available")
@click.option("--day", type=click.DateTime(["%Y-%m-%d"]), default=None)
@click.option("--endorsement", default=None, help="Narrow it to one endorsement.")
@click.pass_obj
def available(console: Console, day: datetime | None, endorsement: str | None) -> None:
    """Everybody who can be named on a permit for a day."""
    when = day.date() if day else date.today()
    wanted = _endorsement(endorsement) if endorsement else None
    with console.work() as work:
        register = _register(work)
        found = register.available(when, wanted)
        for one in found:
            company = register.company(one.company)
            say(one.card, one.name, company.name, company.telephone)
    if not found:
        sys.stderr.write("nobody is available that day\n")


def _register(work: UnitOfWork) -> EscortRegister:
    """The register off the rows, which the store already knows how to build."""
    return escorts_from(work.db)


def _endorsement(written: str) -> Endorsement:
    """One endorsement by its printed name, refusing anything else."""
    try:
        return Endorsement(written)
    except ValueError:
        raise Unreadable(
            f"{written!r} is not an endorsement this office issues",
            endorsement=written,
        ) from None
