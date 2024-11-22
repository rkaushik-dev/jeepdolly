"""Builders the suite shares, so a test says what it is arguing about.

Every number here can be worked back to something a hauler would put on an
application. The tractor and lowboy below is the one the office sees ten times
a week: a day cab pulling a three axle lowboy with an excavator on it.

The network is nine places in the middle of the state with twelve segments
between them, which is enough to have two ways round most things and one way
into Anson. It is not a map of anywhere. It is a road log small enough that a
person can check a route by hand and big enough that the router has to work.
"""

from datetime import date

from jeepdolly.conditions.library import standard_library
from jeepdolly.domain.axle import Axle, AxleKind
from jeepdolly.domain.carrier import Bond, Carrier, CarrierRegister, Insurance
from jeepdolly.domain.combination import Combination, Unit, UnitKind
from jeepdolly.domain.commodity import Piece
from jeepdolly.domain.commodity import standard_book as standard_commodities
from jeepdolly.domain.envelope import Envelope
from jeepdolly.domain.highway import Jurisdiction, Route
from jeepdolly.domain.network import Network, Node, Segment
from jeepdolly.domain.restriction import RestrictionBook
from jeepdolly.domain.stream import AxleStream
from jeepdolly.fees.schedule import standard_book as standard_schedules
from jeepdolly.limits.size import standard_book as standard_sizes
from jeepdolly.limits.statutory import standard_book as standard_statutes
from jeepdolly.measure import Inches, Miles, Pounds
from jeepdolly.money import Dollars
from jeepdolly.permits.application import Applicant, Application
from jeepdolly.permits.issue import Office
from jeepdolly.permits.numbering import Numbering
from jeepdolly.seasons import SeasonBook
from jeepdolly.structures.inventory import Structure, StructureBook, StructureKind

STEER = Axle(AxleKind.STEER, Pounds(12000), tires=2, tire_width=Inches(16))

#: town, county, district for each place in the test network.
PLACES = {
    "waterville": ("Kennebec", 3),
    "fairfield": ("Somerset", 3),
    "skowhegan": ("Somerset", 4),
    "norridgewock": ("Somerset", 4),
    "madison": ("Somerset", 4),
    "anson": ("Somerset", 4),
    "canaan": ("Somerset", 4),
    "pittsfield": ("Somerset", 3),
    "newport": ("Penobscot", 3),
}

#: ident, route, start, end, hundredths of a mile, one way.
ROADS = [
    ("I95-1", "I-95", "waterville", "fairfield", 460, False),
    ("I95-2", "I-95", "fairfield", "pittsfield", 1420, False),
    ("US201-1", "US 201", "waterville", "fairfield", 510, False),
    ("US201-2", "US 201", "fairfield", "skowhegan", 1240, False),
    ("US201-3", "US 201", "skowhegan", "anson", 870, False),
    ("US2-1", "US 2", "skowhegan", "norridgewock", 620, False),
    ("US2-2", "US 2", "pittsfield", "newport", 890, False),
    ("SR8-1", "SR 8", "norridgewock", "madison", 730, False),
    ("SA148-1", "SA 148", "norridgewock", "canaan", 940, False),
    ("SA148-2", "SA 148", "canaan", "skowhegan", 760, False),
    ("SR43-1", "SR 43", "canaan", "pittsfield", 1010, False),
    ("TR104-1", "TR 104", "madison", "anson", 320, False),
]


def axle(weight, kind=AxleKind.TRAILER, **kw):
    """One axle by weight, trailer unless told otherwise."""
    return Axle(kind, Pounds(weight), **kw)


def make_stream(weights, spacings, kinds=None):
    """A stream from bare numbers, which is how the tests talk about one."""
    kinds = kinds or [AxleKind.TRAILER] * len(weights)
    axles = tuple(
        Axle(kind, Pounds(weight), tires=2, tire_width=Inches(16))
        if kind is AxleKind.STEER
        else Axle(kind, Pounds(weight))
        for kind, weight in zip(kinds, weights, strict=True)
    )
    return AxleStream(axles, tuple(Inches(gap) for gap in spacings))


def tractor_lowboy():
    """Day cab, tandem drive, three axle lowboy. 94,000 lb over 44 ft 10 in."""
    return make_stream(
        [12000, 17000, 17000, 16000, 16000, 16000],
        [200, 54, 180, 52, 52],
        [
            AxleKind.STEER,
            AxleKind.DRIVE,
            AxleKind.DRIVE,
            AxleKind.TRAILER,
            AxleKind.TRAILER,
            AxleKind.TRAILER,
        ],
    )


def jeeped_lowboy():
    """The same tractor with a two axle jeep and a booster under a transformer."""
    return make_stream(
        [12000, 19500, 19500, 18000, 18000, 20000, 20000, 20000, 18000],
        [204, 54, 62, 60, 148, 60, 60, 132],
        [
            AxleKind.STEER,
            AxleKind.DRIVE,
            AxleKind.DRIVE,
            AxleKind.JEEP,
            AxleKind.JEEP,
            AxleKind.TRAILER,
            AxleKind.TRAILER,
            AxleKind.TRAILER,
            AxleKind.BOOSTER,
        ],
    )


def legal_semi():
    """Five axles, eighty thousand, tandems thirty six feet apart."""
    return make_stream(
        [12000, 17000, 17000, 17000, 17000],
        [200, 54, 324, 54],
        [
            AxleKind.STEER,
            AxleKind.DRIVE,
            AxleKind.DRIVE,
            AxleKind.TRAILER,
            AxleKind.TRAILER,
        ],
    )


def place(name):
    county, district = PLACES[name]
    return Jurisdiction(county, name.title(), district)


def road_log():
    """Nine places, twelve segments, two ways round most things."""
    network = Network()
    for name in PLACES:
        network.add_node(Node(name, name.title()))
    for ident, route, start, end, hundredths, one_way in ROADS:
        network.add_segment(
            Segment(
                ident,
                Route.parse(route),
                start,
                end,
                Miles(hundredths),
                place(start),
                one_way=one_way,
            )
        )
    return network


#: ident, name, segment, kind, spans, built, operating, inventory, posted, clearance.
STRUCTURES = [
    ("1247", "Kennebec River", "US201-2", "bridge", (120, 120, 120), 1937, 132000, 88000, 0, 0),
    ("1533", "Carrabassett Stream", "SA148-1", "bridge", (48,), 1954, 60000, 42000, 48000, 0),
    ("2201", "Norridgewock overpass", "US2-1", "overpass", (), 1972, 0, 0, 0, 165),
    ("3310", "Martin Stream", "SR43-1", "culvert", (14,), 1968, 90000, 70000, 0, 0),
    ("4102", "Pittsfield gantry", "I95-2", "sign gantry", (), 1998, 0, 0, 0, 198),
    ("5006", "Sandy River", "SR8-1", "bridge", (90, 90), 1961, 110000, 76000, 0, 0),
]


def a_carrier_register():
    """One carrier in good standing and one that is not."""
    register = CarrierRegister()
    cover = Insurance(
        "CA-88214", "Acadia Mutual", Dollars.of(1000000), date(2025, 1, 1), date(2025, 12, 31)
    )
    bond = Bond(
        "SB-4471", "Penobscot Surety", Dollars.of(25000), date(2025, 1, 1), date(2025, 12, 31)
    )
    register.add(
        Carrier(1204471, "Bickford Heavy Haul", "Newport", "ME", insurance=cover, bond=bond)
    )
    register.add(Carrier(2299001, "Aroostook Crane Service", "Presque Isle", "ME"))
    return register


def an_office(restrictions=None, seasons=None):
    """The whole office wired up against the test network."""
    return Office(
        network=road_log(),
        restrictions=restrictions or RestrictionBook(),
        seasons=seasons or SeasonBook(),
        statutes=standard_statutes(),
        sizes=standard_sizes(),
        commodities=standard_commodities(),
        carriers=a_carrier_register(),
        structures=structure_book(),
        schedules=standard_schedules(),
        library=standard_library(),
        numbering=Numbering(),
    )


def a_combination(stream=None, envelope=None):
    """Tractor and lowboy, plated, with the axle counts made to agree."""
    stream = stream or tractor_lowboy()
    counts = {6: (3, 3), 9: (3, 2, 3, 1), 5: (3, 2), 3: (2, 1)}[stream.count]
    kinds = [UnitKind.TRACTOR, UnitKind.JEEP, UnitKind.LOWBOY, UnitKind.BOOSTER]
    if len(counts) == 2:
        kinds = [UnitKind.TRACTOR, UnitKind.LOWBOY]
    units = tuple(
        Unit(kind, axles, f"ME {4470 + index}", Pounds(9000 * axles))
        for index, (kind, axles) in enumerate(zip(kinds, counts, strict=True))
    )
    return Combination(units, stream, envelope or Envelope.read("12'", "13'6", "82'", "0", "6'"))


def an_application(reference="25-04471", **kw):
    """A filed application for the excavator move everybody argues about."""
    fields = {
        "usdot": 1204471,
        "commodity": "EXCV",
        "pieces": (Piece(standard_commodities().get("EXCV"), "Cat 336 excavator", "TMD01297"),),
        "combination": a_combination(),
        "origin": "waterville",
        "destination": "pittsfield",
        "start": date(2025, 4, 8),
        "end": date(2025, 4, 10),
        "applicant": Applicant("Dana Bickford", "207 555 0143"),
        "filed": date(2025, 4, 7),
    }
    fields.update(kw)
    return Application(reference, **fields)


def structure_book():
    """Six structures on the test network, two of them worth worrying about."""
    book = StructureBook()
    for ident, name, segment, kind, spans, built, operating, inventory, posted, high in STRUCTURES:
        book.add(
            Structure(
                ident,
                name,
                segment,
                StructureKind(kind),
                spans_feet=spans,
                built=built,
                inspected=date(2024, 6, 1),
                operating_rating=Pounds(operating) if operating else None,
                inventory_rating=Pounds(inventory) if inventory else None,
                posted=Pounds(posted) if posted else None,
                clearance=Inches(high) if high else None,
            )
        )
    return book
