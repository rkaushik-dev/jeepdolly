"""One log line per request, with the things somebody would search for.

The line is written after the response, because the status and the duration
are what make it worth having. A line written on the way in tells you a
request arrived, which is the least interesting fact about it.

Four fields and they are chosen for grepping rather than for completeness.
The method and path say what was asked. The status says what happened. The
duration says whether it was slow, which is the question asked about the
route check and about nothing else.

The query string is not logged. It carries the load, the dates and sometimes
a reason typed by a clerk, and none of that belongs in a file that gets
shipped to a log server. The path does carry the permit number and the usdot,
which is exactly what somebody greps for, and that is deliberate.

A refusal is logged at info like anything else. A refusal is the service
working. Only an unhandled exception is an error, and that one is logged with
the exception type and re raised so the framework can produce its own answer.
"""

from __future__ import annotations

import time
from collections.abc import Awaitable, Callable

from fastapi import FastAPI, Request, Response

from .. import logs

#: Requests slower than this are worth a second look.
SLOW_MILLISECONDS = 500


def duration_since(started: float) -> int:
    return int((time.perf_counter() - started) * 1000)


def install(app: FastAPI) -> None:
    """Put one log line behind every request this application answers."""

    @app.middleware("http")
    async def log_request(
        request: Request, call_next: Callable[[Request], Awaitable[Response]]
    ) -> Response:
        started = time.perf_counter()
        try:
            answer = await call_next(request)
        except Exception as trouble:
            logs.failure(
                "request.failed",
                method=request.method,
                path=request.url.path,
                error=type(trouble).__name__,
                ms=duration_since(started),
            )
            raise
        spent = duration_since(started)
        written = {
            "method": request.method,
            "path": request.url.path,
            "status": answer.status_code,
            "ms": spent,
        }
        if spent >= SLOW_MILLISECONDS:
            logs.trouble("request.slow", **written)
        else:
            logs.event("request.done", **written)
        return answer
