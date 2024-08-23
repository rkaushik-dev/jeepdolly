"""One root for every deliberate refusal, and a code that never changes.

A permit office says no a great deal, and it has to say no the same way twice.
The hauler gets a letter, the clerk gets a line on the screen, the carrier's
software gets a code, and all three have to be the same refusal or somebody
will argue that they were told two different things.

So every refusal in this system is a ``Refusal`` with a code on the class. The
code is a stable string. It goes in the letter, in the log, and in the http
body, and it does not change when the wording does.

There are seven of them and that is deliberate. A refusal a clerk cannot sort
into one of these is a refusal nobody can act on.

``unreadable``   what was written cannot be read as what it claims to be
``impossible``   the description contradicts itself before any rule is applied
``not_on_file``  something is referred to that the office does not have
``conflict``     it collides with something already on file
``over_limit``   it is past a published limit
``blocked``      the road, the season or the clock forbids it
``not_allowed``  the state of the document or the person asking forbids it

A ``TypeError`` is not on this list on purpose. Handing a bare integer where a
weight belongs is not a refusal, it is a defect in the caller, and dressing it
up as a refusal puts a programming mistake in front of a hauler.
"""

from __future__ import annotations

from typing import Any


class Refusal(Exception):
    """A deliberate no, with a stable code and the facts behind it."""

    code = "refused"

    def __init__(self, message: str, **facts: Any) -> None:
        super().__init__(message)
        self.message = message
        self.facts = facts

    def as_dict(self) -> dict[str, Any]:
        """The shape the http layer and the log both write."""
        return {"code": self.code, "message": self.message, "facts": dict(self.facts)}

    def __str__(self) -> str:
        return f"{self.code}: {self.message}"


class Unreadable(Refusal):
    """What was written cannot be read as what it claims to be."""

    code = "unreadable"


class Impossible(Refusal):
    """The description contradicts itself before any rule is applied."""

    code = "impossible"


class NotOnFile(Refusal):
    """Something is referred to that the office does not have."""

    code = "not_on_file"


class Conflict(Refusal):
    """It collides with something already on file."""

    code = "conflict"


class OverLimit(Refusal):
    """It is past a published limit."""

    code = "over_limit"


class Blocked(Refusal):
    """The road, the season or the clock forbids it."""

    code = "blocked"


class NotAllowed(Refusal):
    """The state of the document, or the person asking, forbids it."""

    code = "not_allowed"


#: Every refusal the system can raise, by code, for the api error table.
BY_CODE: dict[str, type[Refusal]] = {
    kind.code: kind
    for kind in (Unreadable, Impossible, NotOnFile, Conflict, OverLimit, Blocked, NotAllowed)
}
