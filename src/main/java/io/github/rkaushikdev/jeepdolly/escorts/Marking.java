package io.github.rkaushikdev.jeepdolly.escorts;

import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import io.github.rkaushikdev.jeepdolly.travel.TravelLimits;
import java.util.ArrayList;
import java.util.List;

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
 */
public record Marking(int banners, int flags, int lamps, int beacons, List<String> reasons) {

    /** Anything past this width wears a banner and flags. */
    public static final Inches LEGAL_WIDTH = Inches.of(8, 6);

    /** Anything past this length wears a banner. */
    public static final Inches LEGAL_LENGTH = Inches.of(75);

    /** A projection past this gets its own flags. */
    public static final Inches FLAGGED_OVERHANG = Inches.of(4);

    public Marking {
        reasons = List.copyOf(reasons);
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
        if (envelope.rearOverhang().compareTo(FLAGGED_OVERHANG) > 0) {
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
        return new Marking(banners, byDay ? corners : 0, afterDark ? corners : 0, beacons, reasons);
    }

    /** Whether the load wears anything at all. */
    public boolean any() {
        return banners != 0 || flags != 0 || lamps != 0 || beacons != 0;
    }

    /** Whether this is the night marking, with lamps in place of flags. */
    public boolean afterDark() {
        return lamps != 0 && flags == 0;
    }

    /** Whether the load wears its flags by day and carries its lamps for after dark. */
    public boolean roundTheClock() {
        return lamps != 0 && flags != 0;
    }

    /** The marking lines as they print on the permit face. */
    public List<String> conditions() {
        if (!any()) {
            return List.of();
        }
        List<String> found = new ArrayList<>();
        if (banners != 0) {
            found.add(banners + " OVERSIZE LOAD banners, one front and one rear, seven feet by eighteen inches");
        }
        if (flags != 0) {
            found.add(flags + " eighteen inch red flags on the corners and widest points");
        }
        if (roundTheClock()) {
            found.add(lamps + " amber lamps at the same places, lit from sunset to sunrise");
        } else if (lamps != 0) {
            found.add(lamps + " amber lamps in place of flags, lit for the whole move");
        }
        if (beacons != 0) {
            found.add(beacons + " amber beacons on the power unit, visible from the rear");
        }
        return List.copyOf(found);
    }

    /** As the counter says it: {@code 2 oversize load banners, 2 flags, 1 beacons}. */
    @Override
    public String toString() {
        if (!any()) {
            return "no marking";
        }
        List<String> parts = new ArrayList<>();
        if (banners != 0) {
            parts.add(banners + " oversize load banners");
        }
        if (flags != 0) {
            parts.add(flags + " flags");
        }
        if (lamps != 0) {
            parts.add(lamps + " lamps");
        }
        if (beacons != 0) {
            parts.add(beacons + " beacons");
        }
        return String.join(", ", parts);
    }
}
