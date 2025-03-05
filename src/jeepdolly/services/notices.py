"""The letters, which are the only thing a hauler outside the office reads.

A notice is plain text with a fixed shape: who it is to, what it is about, the
body, and a line saying who to ring. It is faxed as often as it is emailed,
which is why there is no markup in it and why the lines are short enough to
survive a fax.

The refusal letter is the important one. It says every reason, in the order
the assessment found them, and it never says anything else. A letter that
gives one reason gets a hauler who fixes that one thing and comes back, and
the office refuses him again for the second reason, and now it is the office
being difficult rather than the load being wrong.

The letters do not decide anything. Each one takes what the office already
worked out and lays it out, so a change to a rule cannot be hidden in a
letter, and a change to a letter cannot change what was decided.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date

from ..errors import Impossible
from ..permits.application import Application
from ..permits.permit import Permit

LINE_LENGTH = 72

OFFICE = "Motor Carrier Permit Office"
TELEPHONE = "207 555 0100"


@dataclass(frozen=True)
class Notice:
    """One letter, ready to fax."""

    to: str
    about: str
    day: date
    body: tuple[str, ...]

    def lines(self) -> tuple[str, ...]:
        found = [
            OFFICE,
            self.day.isoformat(),
            "",
            f"To: {self.to}",
            f"Re: {self.about}",
            "",
        ]
        found.extend(self.body)
        found.extend(["", f"Questions to the permit office on {TELEPHONE}."])
        return tuple(found)

    def text(self) -> str:
        return "\n".join(self.lines())

    @property
    def widest(self) -> int:
        return max(len(one) for one in self.lines())

    def __str__(self) -> str:
        return f"{self.about} to {self.to}"


def wrap(sentence: str, width: int = LINE_LENGTH) -> tuple[str, ...]:
    """Break a sentence at spaces, because a fax has a right margin."""
    if width < 20:
        raise Impossible("a letter is wider than that")
    words = sentence.split()
    lines: list[str] = []
    standing = ""
    for word in words:
        if not standing:
            standing = word
        elif len(standing) + 1 + len(word) <= width:
            standing = f"{standing} {word}"
        else:
            lines.append(standing)
            standing = word
    if standing:
        lines.append(standing)
    return tuple(lines)


def refusal_letter(application: Application, reasons: tuple[str, ...], day: date) -> Notice:
    """Every reason the office found, in the order it found them."""
    if not reasons:
        raise Impossible("a refusal letter says why")
    body = list(
        wrap(
            f"The application {application.reference} to move from "
            f"{application.origin.title()} to {application.destination.title()} "
            f"between {application.start.isoformat()} and {application.end.isoformat()} "
            "cannot be permitted as submitted. The reasons are set out below."
        )
    )
    body.append("")
    for number, reason in enumerate(reasons, start=1):
        for index, written in enumerate(wrap(reason, LINE_LENGTH - 4)):
            body.append(f"{number:>2}. {written}" if index == 0 else f"    {written}")
    body.append("")
    body.extend(
        wrap(
            "A revised application may be submitted at any time. Nothing in "
            "this letter is a decision about any other application."
        )
    )
    return Notice(
        application.applicant.name, f"application {application.reference}", day, tuple(body)
    )


def issue_letter(permit: Permit, day: date) -> Notice:
    """What goes out with the permit itself."""
    body = list(wrap(f"Permit {permit.number} is enclosed and is valid as written."))
    body.append("")
    body.extend(permit.face().splitlines())
    body.append("")
    body.append("Conditions:")
    for condition in permit.conditions:
        for index, written in enumerate(wrap(condition.text, LINE_LENGTH - 4)):
            body.append(f"  - {written}" if index == 0 else f"    {written}")
    return Notice(permit.carrier_name, f"permit {permit.number}", day, tuple(body))


def expiry_notice(permits: tuple[Permit, ...], to: str, day: date) -> Notice:
    """The Friday telephone call, in writing."""
    if not permits:
        raise Impossible("an expiry notice names at least one permit")
    body = list(wrap("The following permits expire within the next few days."))
    body.append("")
    for permit in permits:
        body.append(f"  {permit.number}  expires {permit.end.isoformat()}")
    body.append("")
    body.extend(
        wrap(
            "A permit cannot be extended. A move that will not be finished by "
            "the last day needs a new permit."
        )
    )
    return Notice(to, "permits expiring", day, tuple(body))
