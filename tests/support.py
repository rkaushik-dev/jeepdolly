"""Builders the suite shares, so a test says what it is arguing about.

Every number here can be worked back to something a hauler would put on an
application. The tractor and lowboy below is the one the office sees ten times
a week: a day cab pulling a three axle lowboy with an excavator on it.
"""

from jeepdolly.domain.axle import Axle, AxleKind
from jeepdolly.domain.stream import AxleStream
from jeepdolly.measure import Inches, Pounds

STEER = Axle(AxleKind.STEER, Pounds(12000), tires=2, tire_width=Inches(16))


def axle(weight, kind=AxleKind.TRAILER, **kw):
    """One axle by weight, trailer unless told otherwise."""
    return Axle(kind, Pounds(weight), **kw)


def make_stream(weights, spacings, kinds=None):
    """A stream from bare numbers, which is how the tests talk about one."""
    kinds = kinds or [AxleKind.TRAILER] * len(weights)
    axles = tuple(
        Axle(kind, Pounds(weight), tires=2 if kind is AxleKind.STEER else 4)
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
