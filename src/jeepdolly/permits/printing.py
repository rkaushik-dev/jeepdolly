"""The printed permit, which is the only artefact anybody outside sees.

It goes on one sheet of paper, folded once, and lives on the dash of a truck
for a week. So it is eighty columns wide, it has no colour, and the things an
enforcement officer looks for are in the first six lines: the number, the
carrier, the dates, the size and weight, and where it may go.

The conditions come after that, numbered, in the order the office prints them.
Numbering matters: an officer at the roadside says condition four, and the
hauler has to be able to find condition four.

The permit is printed from the permit record alone. It does not read the
carrier table, the road log or the fee schedule, because the paper has to say
what was decided on the day it was written, and everything else has moved on.

A duplicate is the same sheet with a copy number on it. The copy number is
passed in rather than read off the permit, because the permit does not know
how many times it has been printed and should not: the reprint register knows,
and it is a list beside the permit rather than a field on it.
"""

from __future__ import annotations

from ..errors import Impossible
from .permit import Permit

WIDTH = 80
OFFICE = "MOTOR CARRIER PERMIT OFFICE"


def rule(character: str = "=") -> str:
    return character * WIDTH


def centred(text: str) -> str:
    if len(text) >= WIDTH:
        return text
    spare = WIDTH - len(text)
    return " " * (spare // 2) + text


def two_up(left: str, right: str) -> str:
    """Two fields on one line, the second one right against the margin."""
    if len(left) + len(right) + 2 > WIDTH:
        return f"{left}  {right}"
    return left + " " * (WIDTH - len(left) - len(right)) + right


def wrapped(text: str, indent: int = 4) -> tuple[str, ...]:
    room = WIDTH - indent
    words = text.split()
    lines: list[str] = []
    standing = ""
    for word in words:
        if not standing:
            standing = word
        elif len(standing) + 1 + len(word) <= room:
            standing = f"{standing} {word}"
        else:
            lines.append(standing)
            standing = word
    if standing:
        lines.append(standing)
    return tuple(lines)


def printed(permit: Permit, copy: int = 1) -> tuple[str, ...]:
    """The whole sheet, as lines, ready for a line printer.

    A copy number above one puts a banner under the office name and another
    across the foot. Two banners on one sheet is deliberate: the top one is
    what a clerk sees on the pile and the bottom one is what survives when
    somebody folds the sheet to put it behind a windscreen.
    """
    if not permit.conditions:
        raise Impossible(f"{permit.number} has no conditions and cannot be printed")
    lines = [
        rule(),
        centred(OFFICE),
        centred("OVERSIZE AND OVERWEIGHT PERMIT"),
    ]
    if copy > 1:
        lines.append(centred(f"*** DUPLICATE - COPY {copy} ***"))
    lines += [
        rule(),
        two_up(f"PERMIT {permit.number}", f"ISSUED {permit.issued.isoformat()}"),
        two_up(
            f"{permit.carrier_name} ({permit.usdot})",
            f"VALID {permit.start.isoformat()} TO {permit.end.isoformat()}",
        ),
        rule("-"),
        two_up(f"GROSS {permit.gross}", f"DISTANCE {permit.distance}"),
        f"SIZE  {permit.envelope}",
        rule("-"),
        "ROUTE",
    ]
    lines.extend(f"    {one}" for one in wrapped(permit.narrative))
    lines.append(rule("-"))
    lines.append("CONDITIONS")
    for number, condition in enumerate(permit.conditions, start=1):
        body = wrapped(condition.text, indent=8)
        lines.append(f"  {number:>2}. {body[0]}")
        lines.extend(f"      {one}" for one in body[1:])
    lines.append(rule("-"))
    lines.append("FEES")
    for line in permit.quote.lines:
        lines.append(two_up(f"    {line.description}", str(line.amount)))
    lines.append(two_up("    TOTAL", str(permit.quote.total)))
    lines.append(rule())
    if copy > 1:
        lines.append(centred(f"*** DUPLICATE - COPY {copy} ***"))
        lines.append(rule())
    if permit.state.value != "issued":
        lines.append(centred(f"*** {permit.state.value.upper()} ***"))
        lines.append(rule())
    return tuple(lines)


def sheet(permit: Permit, copy: int = 1) -> str:
    """The printed permit as one piece of text."""
    return "\n".join(printed(permit, copy)) + "\n"


def widest(permit: Permit, copy: int = 1) -> int:
    """The longest line, which has to fit the paper."""
    return max(len(one) for one in printed(permit, copy))
