"""The road network as the office holds it: nodes, segments, and what joins.

The state's road log is a list of segments. A segment runs between two nodes,
carries one route designation, has a length in hundredths of a mile, and
belongs to one class of road and one town. That is the whole of it, and every
route check in this system is a walk down a list of these.

Nodes are intersections, town lines, bridges worth naming, and the ends of
things. They are named rather than numbered where anybody would name them,
because a permit that says turn at Skowhegan is a permit a driver can follow
and a permit that says turn at node 41127 is not.

A segment is one way or two way. Most are two way. The one way segments are
ramps and a handful of village streets, and they matter more than their number
suggests: a route that runs the wrong way down a ramp is the kind of mistake
that gets found by a driver at two in the morning.

The network does not know anything about weight, height, or season. Those are
restrictions, they hang off segments, and they live in their own file, because
they change weekly and the road log changes twice a year.
"""

from __future__ import annotations

from dataclasses import dataclass

from ..errors import Conflict, Impossible, NotOnFile
from ..measure import Miles
from .highway import HighwayClass, Jurisdiction, Route


@dataclass(frozen=True)
class Node:
    """A place a route can turn, start, or end."""

    name: str
    town: str

    def __post_init__(self) -> None:
        if not self.name.strip():
            raise Impossible("a node is named")

    def __str__(self) -> str:
        return self.name


@dataclass(frozen=True)
class Segment:
    """One piece of road between two nodes, off the state road log."""

    ident: str
    route: Route
    start: str
    end: str
    length: Miles
    jurisdiction: Jurisdiction
    one_way: bool = False

    def __post_init__(self) -> None:
        if self.start == self.end:
            raise Impossible(f"segment {self.ident} starts and ends in the same place")
        if self.length.hundredths <= 0:
            raise Impossible(f"segment {self.ident} has no length")

    @property
    def highway_class(self) -> HighwayClass:
        return self.route.highway_class

    def runs_from(self, node: str) -> bool:
        """Whether this segment may be entered at ``node``."""
        if node == self.start:
            return True
        return node == self.end and not self.one_way

    def other_end(self, node: str) -> str:
        if node == self.start:
            return self.end
        if node == self.end:
            return self.start
        raise Impossible(f"segment {self.ident} does not touch {node}")

    def __str__(self) -> str:
        return f"{self.ident} {self.route} {self.start} to {self.end} {self.length}"


class Network:
    """Every segment on file, with the joins worked out once."""

    def __init__(self) -> None:
        self._nodes: dict[str, Node] = {}
        self._segments: dict[str, Segment] = {}
        self._touching: dict[str, list[str]] = {}

    def add_node(self, node: Node) -> Node:
        if node.name in self._nodes:
            raise Conflict(f"node {node.name} is already on file", node=node.name)
        self._nodes[node.name] = node
        self._touching[node.name] = []
        return node

    def add_segment(self, segment: Segment) -> Segment:
        if segment.ident in self._segments:
            raise Conflict(f"segment {segment.ident} is already on file", segment=segment.ident)
        for end in (segment.start, segment.end):
            if end not in self._nodes:
                raise NotOnFile(f"segment {segment.ident} names node {end}, which is not on file")
        self._segments[segment.ident] = segment
        self._touching[segment.start].append(segment.ident)
        self._touching[segment.end].append(segment.ident)
        return segment

    def node(self, name: str) -> Node:
        try:
            return self._nodes[name]
        except KeyError:
            raise NotOnFile(f"no node called {name}", node=name) from None

    def segment(self, ident: str) -> Segment:
        try:
            return self._segments[ident]
        except KeyError:
            raise NotOnFile(f"no segment {ident}", segment=ident) from None

    def leaving(self, node: str) -> tuple[Segment, ...]:
        """Segments that can be driven away from this node."""
        self.node(node)
        found = [self._segments[ident] for ident in self._touching[node]]
        return tuple(sorted((one for one in found if one.runs_from(node)), key=lambda s: s.ident))

    def between(self, start: str, end: str) -> tuple[Segment, ...]:
        """Segments joining two nodes directly, in either direction."""
        found = [one for one in self.leaving(start) if one.other_end(start) == end]
        return tuple(found)

    def on_route(self, route: Route) -> tuple[Segment, ...]:
        found = [one for one in self._segments.values() if one.route == route]
        return tuple(sorted(found, key=lambda one: one.ident))

    def in_district(self, district: int) -> tuple[Segment, ...]:
        found = [one for one in self._segments.values() if one.jurisdiction.district == district]
        return tuple(sorted(found, key=lambda one: one.ident))

    def segments(self) -> tuple[Segment, ...]:
        return tuple(sorted(self._segments.values(), key=lambda one: one.ident))

    def nodes(self) -> tuple[Node, ...]:
        return tuple(sorted(self._nodes.values(), key=lambda one: one.name))

    def __len__(self) -> int:
        return len(self._segments)

    def __contains__(self, ident: object) -> bool:
        return ident in self._segments
