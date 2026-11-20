package io.github.rkaushikdev.jeepdolly.support;

import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.domain.Axle;
import io.github.rkaushikdev.jeepdolly.domain.AxleKind;
import io.github.rkaushikdev.jeepdolly.domain.AxleStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Axle streams the suite shares, so a test says what it is arguing about.
 *
 * <p>Every number here can be worked back to something a hauler would put on an application. The
 * tractor and lowboy is the one the office sees ten times a week: a day cab pulling a three axle
 * lowboy with an excavator on it.
 */
public final class Streams {

    /** A steer axle on two sixteen inch tires. */
    public static final Axle STEER = new Axle(AxleKind.STEER, new Pounds(12000), 2, new Inches(16));

    private static final AxleKind S = AxleKind.STEER;
    private static final AxleKind D = AxleKind.DRIVE;
    private static final AxleKind T = AxleKind.TRAILER;

    private Streams() {}

    /** One trailer axle by weight. */
    public static Axle axle(int weight) {
        return new Axle(AxleKind.TRAILER, new Pounds(weight));
    }

    /** One axle by weight and kind. */
    public static Axle axle(int weight, AxleKind kind) {
        return new Axle(kind, new Pounds(weight));
    }

    /** A stream of trailer axles from bare numbers, which is how the tests talk about one. */
    public static AxleStream make(List<Integer> weights, List<Integer> spacings) {
        return make(weights, spacings, Collections.nCopies(weights.size(), AxleKind.TRAILER));
    }

    /** A stream from bare numbers; a steer axle stands on two sixteen inch tires. */
    public static AxleStream make(List<Integer> weights, List<Integer> spacings, List<AxleKind> kinds) {
        if (kinds.size() != weights.size()) {
            throw new IllegalArgumentException("one kind for each weight");
        }
        List<Axle> axles = new ArrayList<>();
        for (int index = 0; index < weights.size(); index++) {
            AxleKind kind = kinds.get(index);
            Pounds weight = new Pounds(weights.get(index));
            axles.add(kind == AxleKind.STEER ? new Axle(kind, weight, 2, new Inches(16)) : new Axle(kind, weight));
        }
        return new AxleStream(axles, spacings.stream().map(Inches::new).toList());
    }

    /** Day cab, tandem drive, three axle lowboy. 94,000 lb over 44 ft 10 in. */
    public static AxleStream tractorLowboy() {
        return make(
                List.of(12000, 17000, 17000, 16000, 16000, 16000),
                List.of(200, 54, 180, 52, 52),
                List.of(S, D, D, T, T, T));
    }

    /** The same tractor with a two axle jeep and a booster under a transformer. */
    public static AxleStream jeepedLowboy() {
        return make(
                List.of(12000, 19500, 19500, 18000, 18000, 20000, 20000, 20000, 18000),
                List.of(204, 54, 62, 60, 148, 60, 60, 132),
                List.of(S, D, D, AxleKind.JEEP, AxleKind.JEEP, T, T, T, AxleKind.BOOSTER));
    }

    /** Five axles, eighty thousand, tandems thirty six feet apart. */
    public static AxleStream legalSemi() {
        return make(List.of(12000, 17000, 17000, 17000, 17000), List.of(200, 54, 324, 54), List.of(S, D, D, T, T));
    }
}
