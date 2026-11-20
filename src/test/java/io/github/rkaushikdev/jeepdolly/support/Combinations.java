package io.github.rkaushikdev.jeepdolly.support;

import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.domain.AxleStream;
import io.github.rkaushikdev.jeepdolly.domain.Combination;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import io.github.rkaushikdev.jeepdolly.domain.Unit;
import io.github.rkaushikdev.jeepdolly.domain.UnitKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Plated equipment over the suite's streams, with the axle counts made to agree. */
public final class Combinations {

    /** Axles per unit for each stream length the suite uses. */
    private static final Map<Integer, List<Integer>> COUNTS =
            Map.of(6, List.of(3, 3), 9, List.of(3, 2, 3, 1), 5, List.of(3, 2), 3, List.of(2, 1));

    private Combinations() {}

    /** The box the excavator move is written for: 12 ft wide, 13 ft 6 in high, 82 ft long. */
    public static Envelope box() {
        return Envelope.read("12'", "13'6", "82'", "0", "6'");
    }

    /** Tractor and lowboy over the tractor and lowboy stream, in the usual box. */
    public static Combination aCombination() {
        return aCombination(Streams.tractorLowboy(), box());
    }

    /** Plated equipment over any stream the suite uses, in the usual box. */
    public static Combination aCombination(AxleStream stream) {
        return aCombination(stream, box());
    }

    /** Tractor and lowboy, plated, with the axle counts made to agree with the stream. */
    public static Combination aCombination(AxleStream stream, Envelope envelope) {
        List<Integer> counts = COUNTS.get(stream.count());
        List<UnitKind> kinds = counts.size() == 2
                ? List.of(UnitKind.TRACTOR, UnitKind.LOWBOY)
                : List.of(UnitKind.TRACTOR, UnitKind.JEEP, UnitKind.LOWBOY, UnitKind.BOOSTER);
        List<Unit> units = new ArrayList<>();
        for (int index = 0; index < counts.size(); index++) {
            int axles = counts.get(index);
            units.add(new Unit(kinds.get(index), axles, "ME " + (4470 + index), new Pounds(9000 * axles)));
        }
        return new Combination(units, stream, envelope);
    }
}
