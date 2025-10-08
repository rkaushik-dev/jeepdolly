"""Settings, read once, from the environment, with defaults that are safe.

Everything configurable in this system is here and nothing anywhere else reads
the environment. That is worth enforcing rather than intending: a service that
reads an environment variable in the middle of a request is a service whose
behaviour cannot be reproduced from its arguments.

The defaults are the ones that are safe to be wrong. The database defaults to
a file in the working directory rather than to a server, so a mistyped
environment cannot point a developer at production. The minimum mileage and
the office name are business defaults and they match the published schedule.

Nothing here is a secret. When there is a password it will arrive as a whole
database url from the environment and it will not be logged, which is why the
url has its own accessor that redacts it for the log line.

The office rules are the second block and they are a different kind of thing.
They are numbers the office itself changes: how many copies a clerk may print,
how far ahead the renewal run looks, how long a carrier has before an unpaid
citation holds them. Every one of them has a default in the module that owns
the rule, and this is how a site overrides one without a deployment.

They are read here rather than from the modules for the reason at the top: a
rule that read its own environment variable would behave differently in a test
than in a request, and the whole point of these numbers is that somebody can
change one and see what happens.
"""

from __future__ import annotations

import os
import re
from dataclasses import dataclass, field

from .enforcement.standing import GRACE_DAYS as ENFORCEMENT_GRACE
from .errors import Impossible
from .permits.reprint import FREELY as REPRINT_FREELY
from .reports.lines import CYCLE_YEARS as SURVEY_CYCLE
from .services.renewals import AHEAD_DAYS as RENEWAL_AHEAD

PREFIX = "JEEPDOLLY_"

DEFAULT_URL = "sqlite+pysqlite:///jeepdolly.sqlite3"
DEFAULT_OFFICE = "Motor Carrier Permit Office"
DEFAULT_MINIMUM_MILES = 10
DEFAULT_REVIEW_DAYS = 14

_PASSWORD = re.compile(r"//([^:/@]+):([^@]+)@")


@dataclass(frozen=True)
class OfficeRules:
    """The numbers the office changes without shipping anything.

    Each one defaults to the constant in the module that owns the rule, so a
    site that sets nothing behaves exactly as the code reads. Nothing here
    changes what a rule means, only where the line is drawn.
    """

    free_copies: int = REPRINT_FREELY
    renewal_days: int = RENEWAL_AHEAD
    citation_grace: int = ENFORCEMENT_GRACE
    line_survey_years: int = SURVEY_CYCLE

    def __post_init__(self) -> None:
        if self.free_copies < 1:
            raise Impossible("an office prints at least one copy without a signature")
        if self.renewal_days < 1:
            raise Impossible("a renewal run looks at least a day ahead")
        if self.citation_grace < 0:
            raise Impossible("a grace period is not negative")
        if self.line_survey_years < 1:
            raise Impossible("a survey cycle is at least a year")

    def lines(self) -> tuple[str, ...]:
        """The rules as they print on the settings page."""
        return (
            f"free copies       {self.free_copies}",
            f"renewal window    {self.renewal_days} days",
            f"citation grace    {self.citation_grace} days",
            f"line survey       {self.line_survey_years} years",
        )

    def __str__(self) -> str:
        return f"{self.free_copies} copies, {self.renewal_days} day renewal window"


@dataclass(frozen=True)
class Settings:
    """Everything the service is allowed to be told."""

    database_url: str = DEFAULT_URL
    office_name: str = DEFAULT_OFFICE
    minimum_miles: int = DEFAULT_MINIMUM_MILES
    review_days: int = DEFAULT_REVIEW_DAYS
    echo_sql: bool = False
    rules: OfficeRules = field(default_factory=OfficeRules)

    def __post_init__(self) -> None:
        if not self.database_url.strip():
            raise Impossible("a database url is not optional")
        if self.minimum_miles < 0:
            raise Impossible("a minimum mileage is not negative")
        if self.review_days < 1:
            raise Impossible("a review turnaround is at least a day")

    @property
    def safe_url(self) -> str:
        """The url with any password taken out, for the log line."""
        return _PASSWORD.sub(r"//\1:***@", self.database_url)

    @property
    def in_memory(self) -> bool:
        return ":memory:" in self.database_url

    def __str__(self) -> str:
        return f"{self.office_name} on {self.safe_url}"


def _read(name: str, fallback: str) -> str:
    return os.environ.get(PREFIX + name, fallback)


def _read_int(name: str, fallback: int) -> int:
    written = _read(name, str(fallback))
    if not written.lstrip("-").isdigit():
        raise Impossible(f"{PREFIX}{name} is a whole number, not {written!r}")
    return int(written)


def _read_flag(name: str, fallback: bool) -> bool:
    written = _read(name, "yes" if fallback else "no").strip().lower()
    if written in ("yes", "true", "on", "1"):
        return True
    if written in ("no", "false", "off", "0"):
        return False
    raise Impossible(f"{PREFIX}{name} is yes or no, not {written!r}")


def from_environment() -> Settings:
    """Build the settings once, at the edge, from the environment."""
    return Settings(
        database_url=_read("DATABASE_URL", DEFAULT_URL),
        office_name=_read("OFFICE_NAME", DEFAULT_OFFICE),
        minimum_miles=_read_int("MINIMUM_MILES", DEFAULT_MINIMUM_MILES),
        review_days=_read_int("REVIEW_DAYS", DEFAULT_REVIEW_DAYS),
        echo_sql=_read_flag("ECHO_SQL", False),
        rules=OfficeRules(
            free_copies=_read_int("FREE_COPIES", REPRINT_FREELY),
            renewal_days=_read_int("RENEWAL_DAYS", RENEWAL_AHEAD),
            citation_grace=_read_int("CITATION_GRACE", ENFORCEMENT_GRACE),
            line_survey_years=_read_int("LINE_SURVEY_YEARS", SURVEY_CYCLE),
        ),
    )
