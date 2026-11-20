package io.github.rkaushikdev.jeepdolly.domain;

import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Pounds;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The whole string, front to back, with the axles it puts on the road.
 *
 * <p>An application describes equipment the way a hauler talks about it: a day cab, a two axle
 * jeep, a nine axle lowboy, a booster. The combination is where that description and the axle
 * stream are made to agree. If a hauler writes a nine axle trailer and then lists eight axles in
 * the stream, the load will not be where the permit says it is, and that is refused here rather
 * than three checks later where the message would be arithmetic.
 *
 * <p>Order is the order down the road. The power unit is first, a jeep goes between the power unit
 * and the trailer, which is the thing people get backwards on the form, and a booster goes on the
 * back.
 */
public record Combination(List<Unit> units, AxleStream stream, Envelope envelope) {

    public Combination {
        units = List.copyOf(units);
        if (units.isEmpty()) {
            throw new Impossible("a combination has equipment in it");
        }
        if (!units.get(0).kind().powered()) {
            throw new Impossible("a combination starts with something that pulls");
        }
        if (units.stream().filter(unit -> unit.kind().powered()).count() > 1) {
            throw new Impossible("a combination is pulled by one power unit");
        }
        if (units.stream().noneMatch(unit -> unit.kind().carriesLoad())) {
            throw new Impossible("a combination has something to put the load on");
        }
        int declared = units.stream().mapToInt(Unit::axles).sum();
        if (declared != stream.count()) {
            throw new Impossible(
                    "the equipment lists " + declared + " axles and the stream holds " + stream.count(),
                    Facts.of("declared", declared, "stream", stream.count()));
        }
        checkOrder(units);
    }

    private static void checkOrder(List<Unit> units) {
        boolean seenDeck = false;
        for (Unit unit : units.subList(1, units.size())) {
            if (unit.kind().carriesLoad()) {
                seenDeck = true;
            } else if (unit.kind() == UnitKind.JEEP && seenDeck) {
                throw new Impossible("a jeep goes ahead of the trailer, not behind it");
            } else if (unit.kind() == UnitKind.BOOSTER && !seenDeck) {
                throw new Impossible("a booster goes behind the trailer, not ahead of it");
            }
        }
    }

    public Unit powerUnit() {
        return units.get(0);
    }

    /** The first unit the load goes on. */
    public Unit deck() {
        return units.stream()
                .filter(unit -> unit.kind().carriesLoad())
                .findFirst()
                .orElseThrow();
    }

    public List<Unit> jeeps() {
        return units.stream().filter(unit -> unit.kind() == UnitKind.JEEP).toList();
    }

    public List<Unit> boosters() {
        return units.stream().filter(unit -> unit.kind() == UnitKind.BOOSTER).toList();
    }

    public int axleCount() {
        return stream.count();
    }

    public Pounds gross() {
        return stream.gross();
    }

    public Pounds tare() {
        Pounds running = Pounds.ZERO;
        for (Unit unit : units) {
            running = running.plus(unit.tare());
        }
        return running;
    }

    /** What the equipment is carrying, which the crane chart has to match. */
    public Pounds payload() {
        return gross().minus(tare());
    }

    /** The string as a hauler would say it out loud: {@code tractor, jeep dolly, lowboy}. */
    public String describe() {
        return units.stream().map(unit -> unit.kind().toString()).collect(Collectors.joining(", "));
    }

    public List<String> plates() {
        return units.stream().map(Unit::plate).toList();
    }

    @Override
    public String toString() {
        return describe() + ", " + axleCount() + " axles, " + gross();
    }
}
