"""Repositories: the queries the office actually runs, named after the asking.

A repository here is not a generic collection with a filter method. It is the
list of questions one part of the office asks about one kind of record, each
with a name a clerk would recognise, and nothing else. That keeps the SQL in
one place per subject and it keeps the odd query honest: if a report needs
something strange, it gets a method with a name that says what it is for, and
the strangeness is visible in review rather than assembled in a service.

Every repository takes a session and holds no state. The unit of work owns the
session and the transaction, and a repository is a way of writing queries
against whichever session it was handed.

Reads hand back domain objects where there is one, and rows where the caller is
going to edit them. That split is deliberate: a caller with a domain object
cannot accidentally save half of it.

This was one module until it was six hundred lines and nine subjects, at
which point a change to the pilot cards meant reading past the permits to
find them. It is a package now, one module per subject, and everything is
re-exported here so that no caller had to change.
"""

from .carriers import CarrierRepository
from .enforcement import CitationRepository
from .escorts import PilotRepository
from .network import NetworkRepository
from .permits import ApplicationRepository, PermitRepository
from .reference import CommodityRepository
from .reprints import ReprintRepository
from .utilities import LineRepository

__all__ = [
    "ApplicationRepository",
    "CarrierRepository",
    "CitationRepository",
    "CommodityRepository",
    "LineRepository",
    "NetworkRepository",
    "PermitRepository",
    "PilotRepository",
    "ReprintRepository",
]
