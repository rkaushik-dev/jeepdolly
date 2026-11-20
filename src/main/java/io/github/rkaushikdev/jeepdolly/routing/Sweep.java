package io.github.rkaushikdev.jeepdolly.routing;

import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import java.util.ArrayList;
import java.util.List;

/**
 * The swept path rule: what radius a load wants, and where on a route it comes up short.
 *
 * <p>The card index in {@link TurnBook} is a record of what the corners are. This is the rule about
 * what a combination needs, and the two change for different reasons: a corner is remeasured when
 * the town rebuilds a junction, and the rule is changed by an order out of the capital.
 *
 * <p>The rule is a swept path in the crudest form that is still honest. A combination needs a radius
 * that grows with its length and with what hangs off the back, and past a certain length nothing
 * gets round at all without going up on the far kerb, which the office will not permit. It does not
 * compute a real swept path, which wants the geometry of the corner and the offtracking of every
 * axle group; the office has a measured radius and a table, and the table is set conservatively. It
 * does not read the direction of travel except to find the corner.
 *
 * <p>A corner comes back in one of two ways and never both. Either the load will not get round it,
 * a {@link TooTight} refusal, or it gets round with little to spare, a {@link CloseRun} condition.
 */
public final class Sweep {

    /** The radius a combination inside the plain length wants, in feet. */
    public static final int PLAIN_RADIUS = 40;

    /** Length at which a combination starts to need more than the plain corner. */
    public static final Inches LONG_ENOUGH = Inches.of(75);

    /** Feet of radius wanted per foot of combination past the plain length. */
    public static final int RADIUS_PER_FOOT = 1;

    /** Feet of radius wanted per foot of rear overhang, which swings wide. */
    public static final int RADIUS_PER_OVERHANG_FOOT = 2;

    /**
     * A corner that clears by less than this gets a condition rather than silence.
     *
     * <p>Ten feet is not a comfort margin. It is the width of the mistake a driver can make on a
     * corner he has not seen before, and the office would rather put a man in the road with a flag
     * than have the trailer find the kerb.
     */
    public static final int MARGIN_FEET = 10;

    private Sweep() {}

    /** The radius this combination wants, in whole feet. A front overhang does not swing wide. */
    public static int radiusWanted(Envelope envelope) {
        Inches over = envelope.length().over(LONG_ENOUGH);
        int fromLength = Math.floorDiv(over.value(), Inches.PER_FOOT) * RADIUS_PER_FOOT;
        int fromOverhang = Math.floorDiv(envelope.rearOverhang().value(), Inches.PER_FOOT) * RADIUS_PER_OVERHANG_FOOT;
        return PLAIN_RADIUS + fromLength + fromOverhang;
    }

    /** Every recorded corner the trip actually turns at, in driven order. */
    public static List<Corner> cornersOn(Itinerary itinerary, TurnBook book) {
        List<Corner> found = new ArrayList<>();
        List<Leg> legs = itinerary.legs();
        for (int index = 0; index + 1 < legs.size(); index++) {
            Leg first = legs.get(index);
            Leg second = legs.get(index + 1);
            book.between(
                            first.leftAt(),
                            first.segment().ident(),
                            second.segment().ident())
                    .ifPresent(found::add);
        }
        return List.copyOf(found);
    }

    /** The corners on this route that this load will not get round. */
    public static List<TooTight> tooTight(Itinerary itinerary, Envelope envelope, TurnBook book) {
        int wanted = radiusWanted(envelope);
        return cornersOn(itinerary, book).stream()
                .filter(one -> one.radiusFeet() < wanted)
                .map(one -> new TooTight(one, wanted))
                .toList();
    }

    /** The corners on this route the load gets round with less than the usual margin to spare. */
    public static List<CloseRun> closeRun(Itinerary itinerary, Envelope envelope, TurnBook book) {
        return closeRun(itinerary, envelope, book, MARGIN_FEET);
    }

    /** The corners on this route the load gets round with less than {@code margin} feet to spare. */
    public static List<CloseRun> closeRun(Itinerary itinerary, Envelope envelope, TurnBook book, int margin) {
        int wanted = radiusWanted(envelope);
        return cornersOn(itinerary, book).stream()
                .filter(one -> wanted <= one.radiusFeet() && one.radiusFeet() < wanted + margin)
                .map(one -> new CloseRun(one, wanted))
                .toList();
    }
}
