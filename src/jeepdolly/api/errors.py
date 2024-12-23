"""One error shape for the whole interface, and the codes that map onto it.

Every failure that leaves this service looks the same:

    {"code": "over_limit", "message": "...", "facts": {...}}

That is the same shape a refusal already carries, so a refusal raised anywhere
in the domain arrives at the client without anybody translating it. The status
code is a lookup on the refusal code and nothing else, which is the only place
in the system that has an opinion about http.

The mapping is worth reading rather than skimming.

``unreadable`` and ``impossible`` are 422. The request was understood and what
it described cannot exist. They are not 400, because 400 is reserved for a body
this service could not parse at all, which the framework raises before any of
this runs.

``not_on_file`` is 404. ``conflict`` is 409. ``not_allowed`` is 409 as well
rather than 403, because it is about the state of a document and not about who
is asking. The one that is about who is asking is authentication, and that
raises its own thing.

``over_limit`` and ``blocked`` are 422 as well, and this is the decision people
argue with. A load that is over a limit is not a bad request: the request was
perfectly good and the answer is no. There is no http status that means that,
so the office picked the one that means the content was understood and refused.
"""

from __future__ import annotations

from typing import Any

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from ..errors import Refusal

#: What each refusal code comes back as.
STATUS = {
    "unreadable": 422,
    "impossible": 422,
    "not_on_file": 404,
    "conflict": 409,
    "over_limit": 422,
    "blocked": 422,
    "not_allowed": 409,
    "refused": 422,
}


def status_for(code: str) -> int:
    return STATUS.get(code, 422)


def body_for(refusal: Refusal) -> dict[str, Any]:
    return refusal.as_dict()


async def refusal_handler(request: Request, refusal: Exception) -> JSONResponse:
    """Turn any refusal into the one shape, with its own status."""
    if not isinstance(refusal, Refusal):  # pragma: no cover - registered per type
        raise refusal
    return JSONResponse(status_code=status_for(refusal.code), content=body_for(refusal))


def install(app: FastAPI) -> None:
    """Register the handler for every refusal the system can raise."""
    app.add_exception_handler(Refusal, refusal_handler)
