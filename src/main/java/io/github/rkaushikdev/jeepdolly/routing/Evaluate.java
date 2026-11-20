package io.github.rkaushikdev.jeepdolly.routing;

import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.SeasonBook;
import io.github.rkaushikdev.jeepdolly.SeasonOrder;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import io.github.rkaushikdev.jeepdolly.domain.Restriction;
import io.github.rkaushikdev.jeepdolly.domain.RestrictionBook;
import io.github.rkaushikdev.jeepdolly.domain.Segment;
import io.github.rkaushikdev.jeepdolly.limits.StatutoryBook;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Walk an itinerary with a load on a day and report what is in the way.
 *
 * <p>This is where the load meets the road, and it is the only place in the system that reads a
 * restriction against a measurement. Everything before it measures. Everything after it prices,
 * prints or refuses.
 *
 * <p>A seasonal posting reads like a fatal finding and is reported as a condition, because the
 * answer is a date rather than a refusal and the hauler wants the date. A corner is always fatal:
 * there is no condition that gets a hundred and forty feet round a thirty eight foot radius.
 *
 * <p>The seasonal case is the one worth reading twice. A segment carries a seasonal restriction
 * naming a region, and the restriction does not carry a weight. The weight comes from whichever
 * thaw order the season book has in force for that region on the day of travel, applied to the
 * statutory gross for that class of road. Three tables have to agree before this rule produces a
 * number, and the number changes when any of them does.
 */
public final class Evaluate {

    private Evaluate() {}

    /** Read every leg of the trip against every restriction on it, with no corners and no clock. */
    public static RouteReading evaluate(
            Itinerary itinerary,
            Envelope envelope,
            Pounds gross,
            LocalDate day,
            RestrictionBook restrictions,
            SeasonBook seasons,
            StatutoryBook statutes) {
        return evaluate(
                itinerary, envelope, gross, day, restrictions, seasons, statutes, Optional.empty(), Optional.empty());
    }

    /** Read every leg of the trip against every restriction on it, and the corners off the card index. */
    public static RouteReading evaluate(
            Itinerary itinerary,
            Envelope envelope,
            Pounds gross,
            LocalDate day,
            RestrictionBook restrictions,
            SeasonBook seasons,
            StatutoryBook statutes,
            TurnBook turns) {
        return evaluate(
                itinerary, envelope, gross, day, restrictions, seasons, statutes, Optional.empty(), Optional.of(turns));
    }

    /**
     * Read every leg of the trip against every restriction on it.
     *
     * @param departure the time the load leaves, or empty when it was not given, in which case every
     *     curfew on the route is a condition
     * @param turns the corner card index, or empty to read no corners at all
     */
    public static RouteReading evaluate(
            Itinerary itinerary,
            Envelope envelope,
            Pounds gross,
            LocalDate day,
            RestrictionBook restrictions,
            SeasonBook seasons,
            StatutoryBook statutes,
            Optional<LocalTime> departure,
            Optional<TurnBook> turns) {
        List<Obstruction> found = new ArrayList<>();
        for (Leg leg : itinerary) {
            found.addAll(segmentObstructions(
                    leg.segment(), envelope, gross, day, restrictions, seasons, statutes, departure));
        }
        turns.ifPresent(book -> found.addAll(cornerObstructions(itinerary, envelope, book)));
        return new RouteReading(day, itinerary, found);
    }

    /**
     * The corners on this route the load will not get round, then the ones it only just gets round.
     *
     * <p>The obstruction is filed against the segment being turned out of, because that is the leg
     * the driver is on when he finds out, and it is the leg a detour would have to replace. A corner
     * the load gets round with little to spare comes back as a condition rather than a refusal.
     */
    public static List<Obstruction> cornerObstructions(Itinerary itinerary, Envelope envelope, TurnBook turns) {
        List<Obstruction> found = new ArrayList<>();
        for (TooTight one : Sweep.tooTight(itinerary, envelope, turns)) {
            found.add(corner(one.corner(), one.wanted(), "corner", true));
        }
        for (CloseRun one : Sweep.closeRun(itinerary, envelope, turns)) {
            found.add(corner(one.corner(), one.wanted(), "tight corner", false));
        }
        return List.copyOf(found);
    }

    private static Obstruction corner(Corner corner, int wanted, String kind, boolean fatal) {
        String note = corner.note().isEmpty() ? "the turn at " + corner.node() : corner.note();
        return new Obstruction(
                corner.outOf(),
                kind,
                note,
                wanted + " ft radius",
                corner.radiusFeet() + " ft radius",
                fatal,
                corner.node());
    }

    /** What is in the way on one segment with no departure time given, which is what the router asks. */
    public static List<Obstruction> segmentObstructions(
            Segment segment,
            Envelope envelope,
            Pounds gross,
            LocalDate day,
            RestrictionBook restrictions,
            SeasonBook seasons,
            StatutoryBook statutes) {
        return segmentObstructions(segment, envelope, gross, day, restrictions, seasons, statutes, Optional.empty());
    }

    /** What is in the way on one segment: the class of road first, then each restriction in force. */
    public static List<Obstruction> segmentObstructions(
            Segment segment,
            Envelope envelope,
            Pounds gross,
            LocalDate day,
            RestrictionBook restrictions,
            SeasonBook seasons,
            StatutoryBook statutes,
            Optional<LocalTime> departure) {
        List<Obstruction> found = new ArrayList<>();
        if (!segment.highwayClass().permittable()) {
            found.add(new Obstruction(
                    segment.ident(),
                    "jurisdiction",
                    "the state does not maintain this road",
                    "a permit from the town",
                    "nothing from this office"));
        }
        for (Restriction restriction : restrictions.onSegment(segment.ident(), day)) {
            read(restriction, envelope, gross, day, seasons, statutes, segment, departure)
                    .ifPresent(found::add);
        }
        return List.copyOf(found);
    }

    private static Optional<Obstruction> read(
            Restriction restriction,
            Envelope envelope,
            Pounds gross,
            LocalDate day,
            SeasonBook seasons,
            StatutoryBook statutes,
            Segment segment,
            Optional<LocalTime> departure) {
        String ident = restriction.segment();
        String note = restriction.note();
        return switch (restriction.kind()) {
            case CLOSURE -> Optional.of(new Obstruction(ident, "closure", note, "open road", "closed"));
            case CLEARANCE ->
                restriction
                        .height()
                        .filter(height -> envelope.height().compareTo(height) > 0)
                        .map(height -> new Obstruction(
                                ident, "clearance", note, envelope.height().toString(), height.toString()));
            case WIDTH ->
                restriction
                        .width()
                        .filter(width -> envelope.width().compareTo(width) > 0)
                        .map(width -> new Obstruction(
                                ident, "width", note, envelope.width().toString(), width.toString()));
            case WEIGHT ->
                restriction
                        .weight()
                        .filter(weight -> gross.compareTo(weight) > 0)
                        .map(weight -> new Obstruction(ident, "posting", note, gross.toString(), weight.toString()));
            case SEASONAL ->
                restriction
                        .region()
                        .flatMap(region -> seasonal(restriction, region, gross, day, seasons, statutes, segment));
            case CURFEW ->
                restriction
                        .curfew()
                        .filter(curfew -> departure.isEmpty() || curfew.covers(day, departure.get()))
                        .map(curfew -> new Obstruction(
                                ident, "curfew", note, "travel outside the curfew", curfew.toString(), false));
        };
    }

    private static Optional<Obstruction> seasonal(
            Restriction restriction,
            String region,
            Pounds gross,
            LocalDate day,
            SeasonBook seasons,
            StatutoryBook statutes,
            Segment segment) {
        Optional<SeasonOrder> order = seasons.inForce(region, day);
        if (order.isEmpty()) {
            return Optional.empty();
        }
        Pounds statutory = statutes.on(segment.highwayClass(), day).gross();
        Pounds allowed = order.get().appliedTo(statutory);
        if (gross.compareTo(allowed) <= 0) {
            return Optional.empty();
        }
        return Optional.of(new Obstruction(
                restriction.segment(),
                "posted season",
                restriction.note() + " under order " + order.get().number(),
                gross.toString(),
                allowed.toString(),
                false));
    }
}
