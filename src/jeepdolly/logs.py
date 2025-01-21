"""Logging as key and value, because the office greps its own logs.

Nobody reads this log with a viewer. When a hauler rings about permit
25-P-004471, somebody greps for it, and what they need back is every line that
mentions it with enough on each line to say what happened. So every line is

    permit.issued number=25-P-004471 usdot=1204471 gross=94000 miles=44

and never a sentence with the number embedded in it. The event name comes
first, unquoted, and everything after it is ``key=value`` with a space
between. Values with a space in them are quoted, values with none are not,
because quoting everything makes the common case harder to read.

There is no structured logging library here and there does not need to be.
This produces a line, hands it to the standard library, and the standard
library decides where it goes. A service that wants JSON in production
configures a formatter and nothing in this file changes.

One rule about what goes in a line: never a whole document, never a body,
never a key. A log line names things and says what happened to them.
"""

from __future__ import annotations

import logging
from typing import Any

LOGGER = "jeepdolly"

#: Anything with one of these in its name is written as three asterisks.
SECRET = ("key", "token", "password", "secret", "digest")


def _quote(value: Any) -> str:
    written = "" if value is None else str(value)
    if written == "":
        return '""'
    if any(one in written for one in (" ", "=", '"')):
        return '"' + written.replace('"', "'") + '"'
    return written


def _hide(name: str) -> bool:
    lowered = name.lower()
    return any(one in lowered for one in SECRET)


def line(event: str, **fields: Any) -> str:
    """One log line: the event, then key and value in the order given."""
    if not event.strip():
        raise ValueError("a log line names an event")
    parts = [event]
    for name, value in fields.items():
        parts.append(f"{name}={'***' if _hide(name) else _quote(value)}")
    return " ".join(parts)


def logger(name: str = LOGGER) -> logging.Logger:
    return logging.getLogger(name)


def event(name: str, **fields: Any) -> None:
    """Write one line at info, which is where the office reads."""
    logger().info(line(name, **fields))


def trouble(name: str, **fields: Any) -> None:
    """Write one line at warning, for something a person should see."""
    logger().warning(line(name, **fields))


def failure(name: str, **fields: Any) -> None:
    """Write one line at error, for something that did not work at all."""
    logger().error(line(name, **fields))


def configure(level: int = logging.INFO, stream: Any = None) -> logging.Logger:
    """Point the office logger at somewhere, once, at start up."""
    found = logger()
    found.setLevel(level)
    found.handlers.clear()
    handler = logging.StreamHandler(stream)
    handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)s %(message)s"))
    found.addHandler(handler)
    found.propagate = False
    return found
