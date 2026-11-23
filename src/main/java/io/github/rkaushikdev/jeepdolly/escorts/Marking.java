package io.github.rkaushikdev.jeepdolly.escorts;

import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import io.github.rkaushikdev.jeepdolly.travel.TravelLimits;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Banners, flags, lamps and beacons: what the load has to wear for one trip.
 *
 * <p>Marking is the cheap half of safety and the half that gets skipped, so the permit spells it
 * out and the enforcement people read it off the permit at the roadside. Four things, and they
 * stack rather than replace each other.
 *
 * <p>A banner reading OVERSIZE LOAD goes on the front and the rear of any load past legal size, and
 * comes off when the load is legal again, because a banner on a legal load is a banner people stop
 * reading. Flags go on the corners of anything that projects and on the widest point of anything
 * over legal width, and the count is on the permit because it is the thing most often short. Lamps
 * replace flags after dark, instead of rather than as well as. A beacon goes on the power unit for
 * any oversize load, and two of them for a superload.
 *
 * <p>Which of flags and lamps a trip wears is a question for its when rules rather than for the
 * load. A load held to daylight never sees the dark and wears flags. A load that may run through
 * the night, which since the interstate width line includes a thirteen foot excavator on I-95, is
 * on the road in both, so it wears the flags and carries the lamps lit from sunset, and the permit
 * says both.
 *
 * <p>The end of a rear overhang is the one place amber is not enough. After dark it carries red,
 * the colour a following driver reads as the back of something, two lamps at the extreme end of the
 * load, because the amber corner lamps are four feet ahead of where the load actually stops.
 *
 * @param worn how many of each thing the load wears, nothing for what it does not
 */
public record Marking(Map<Worn, Integer> worn, List<String> reasons) {

    /** Anything past this width wears a banner and flags. */
    public static final Inches LEGAL_WIDTH = Inches.of(8, 6);

    /** Anything past this length wears a banner. */
    public static final Inches LEGAL_LENGTH = Inches.of(75);

    /** A projection past this gets its own flags. */
    public static final Inches FLAGGED_OVERHANG = Inches.of(4);

    /** What a load wears, in the order the permit face and the counter both list it. */
    public enum Worn {
        BANNERS(
                "oversize load banners",
                "OVERSIZE LOAD banners, one front and one rear, seven feet by eighteen inches"),
        FLAGS("flags", "eighteen inch red flags on the corners and widest points"),
        LAMPS("lamps", "amber lamps in place of flags, lit for the whole move"),
        RED_LAMPS("red lamps", "red lamps at the extreme end of the rear overhang, lit after dark"),
        BEACONS("beacons", "amber beacons on the power unit, visible from the rear");

        private final String spoken;
        private final String printed;

        Worn(String spoken, String printed) {
            this.spoken = spoken;
            this.printed = printed;
        }
    }

    /** What the amber lamps line says when the flags are worn as well, by day. */
    private static final String LAMPS_BESIDE_FLAGS = "amber lamps at the same places, lit from sunset to sunrise";

    public Marking {
        Map<Worn, Integer> kept = new EnumMap<>(Worn.class);
        worn.forEach((what, count) -> {
            if (count != 0) {
                kept.put(what, count);
            }
        });
        worn = Collections.unmodifiableMap(kept);
        reasons = List.copyOf(reasons);
    }

    /** Marking by count, with the reasons written down beside it and no red lamps. */
    public Marking(int banners, int flags, int lamps, int beacons, List<String> reasons) {
        this(Map.of(Worn.BANNERS, banners, Worn.FLAGS, flags, Worn.LAMPS, lamps, Worn.BEACONS, beacons), reasons);
    }

    /** Marking with no reasons written down beside it. */
    public Marking(int banners, int flags, int lamps, int beacons) {
        this(banners, flags, lamps, beacons, List.of());
    }

    /** Read the marking table for an ordinary load on a daylight trip. */
    public static Marking forLoad(Envelope envelope) {
        return forLoad(envelope, false, false);
    }

    /** Read the marking table for one load on a trip wholly by day or wholly after dark. */
    public static Marking forLoad(Envelope envelope, boolean atNight, boolean superload) {
        return read(envelope, !atNight, atNight, superload);
    }

    /**
     * Read the marking table for one load on a trip with these when rules: flags alone for a load
     * held to daylight, flags and lamps for one that may move after dark.
     */
    public static Marking forTrip(Envelope envelope, TravelLimits limits, boolean superload) {
        return read(envelope, true, !limits.daylightOnly(), superload);
    }

    private static Marking read(Envelope envelope, boolean byDay, boolean afterDark, boolean superload) {
        List<String> reasons = new ArrayList<>();
        boolean wide = envelope.width().compareTo(LEGAL_WIDTH) > 0;
        boolean longer = envelope.length().compareTo(LEGAL_LENGTH) > 0;
        int banners = wide || longer ? 2 : 0;
        if (wide) {
            reasons.add("over legal width");
        }
        if (longer) {
            reasons.add("over legal length");
        }

        int corners = wide ? 2 : 0;
        if (envelope.frontOverhang().compareTo(FLAGGED_OVERHANG) > 0) {
            corners += 2;
            reasons.add("front overhang");
        }
        boolean projects = envelope.rearOverhang().compareTo(FLAGGED_OVERHANG) > 0;
        if (projects) {
            corners += 2;
            reasons.add("rear overhang");
        }

        int beacons = 0;
        if (banners != 0) {
            beacons = superload ? 2 : 1;
        }
        if (superload) {
            reasons.add("superload");
        }
        Map<Worn, Integer> worn = new EnumMap<>(Worn.class);
        worn.put(Worn.BANNERS, banners);
        worn.put(Worn.FLAGS, byDay ? corners : 0);
        worn.put(Worn.LAMPS, afterDark ? corners : 0);
        worn.put(Worn.RED_LAMPS, afterDark && projects ? 2 : 0);
        worn.put(Worn.BEACONS, beacons);
        return new Marking(worn, reasons);
    }

    /** How many of one thing the load wears. */
    public int count(Worn what) {
        return worn.getOrDefault(what, 0);
    }

    public int banners() {
        return count(Worn.BANNERS);
    }

    public int flags() {
        return count(Worn.FLAGS);
    }

    /** The amber lamps on the corners and widest points. */
    public int lamps() {
        return count(Worn.LAMPS);
    }

    /** The red lamps at the end of a rear overhang. */
    public int redLamps() {
        return count(Worn.RED_LAMPS);
    }

    public int beacons() {
        return count(Worn.BEACONS);
    }

    /** Whether the load wears anything at all. */
    public boolean any() {
        return !worn.isEmpty();
    }

    /** Whether this is the night marking, with lamps in place of flags. */
    public boolean afterDark() {
        return lamps() != 0 && flags() == 0;
    }

    /** Whether the load wears its flags by day and carries its lamps for after dark. */
    public boolean roundTheClock() {
        return lamps() != 0 && flags() != 0;
    }

    /** The marking lines as they print on the permit face. */
    public List<String> conditions() {
        List<String> found = new ArrayList<>();
        worn.forEach((what, count) -> {
            String printed = what == Worn.LAMPS && roundTheClock() ? LAMPS_BESIDE_FLAGS : what.printed;
            found.add(count + " " + printed);
        });
        return List.copyOf(found);
    }

    /** As the counter says it: {@code 2 oversize load banners, 2 flags, 1 beacons}. */
    @Override
    public String toString() {
        if (!any()) {
            return "no marking";
        }
        List<String> parts = new ArrayList<>();
        worn.forEach((what, count) -> parts.add(count + " " + what.spoken));
        return String.join(", ", parts);
    }
}
