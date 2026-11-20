package io.github.rkaushikdev.jeepdolly.reports;

import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import io.github.rkaushikdev.jeepdolly.permits.PermitState;
import io.github.rkaushikdev.jeepdolly.persistence.models.PermitRow;
import io.github.rkaushikdev.jeepdolly.routing.CloseRun;
import io.github.rkaushikdev.jeepdolly.routing.Corner;
import io.github.rkaushikdev.jeepdolly.routing.Itinerary;
import io.github.rkaushikdev.jeepdolly.routing.Sweep;
import io.github.rkaushikdev.jeepdolly.routing.TooTight;
import io.github.rkaushikdev.jeepdolly.routing.TurnBook;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The corner list, which is the one report that predates the computer.
 *
 * <p>There has been a list of corners on the wall of the counter for as long as anybody remembers:
 * the corners that have caused trouble, tightest first, read before quoting a route rather than
 * after somebody rings up from the middle of a village. This produces the same list off the card
 * index, plus the two things the paper one could never do: what a particular route meets, and what
 * a particular load is going to be stopped by.
 *
 * <p>The permit register comes into it for one number only. A corner that has had forty permits
 * turned at it this year is a corner that is going to have trouble at it. Nothing here decides
 * anything: it reads the card index, the road log and the register, and it prints.
 */
public final class CornerReport {

    /** A corner the load's swept path is too wide for. */
    public static final String STOPPED = "will not get round";

    /** A corner the load gets round inside the margin. */
    public static final String CLOSE = "close";

    /** A corner the load gets round with room to spare. */
    public static final String CLEAR = "clear";

    private CornerReport() {}

    /** One corner as it appears on the list on the wall. */
    public record Wall(Corner corner, int turned) {

        /** The place the corner is at. */
        public String node() {
            return corner.node();
        }

        @Override
        public String toString() {
            String traffic = turned != 0 ? turned + " permits" : "nothing this year";
            return corner + "  (" + traffic + ")";
        }
    }

    /** One corner on one route, and what this load makes of it. */
    public record OnRoute(Corner corner, int wanted, String verdict) {

        /** Feet of radius left over, negative when the load will not get round. */
        public int spare() {
            return corner.radiusFeet() - wanted;
        }

        @Override
        public String toString() {
            return corner.node() + "  " + corner.radiusFeet() + " ft  wants " + wanted + " ft  " + verdict;
        }
    }

    /**
     * How many issued permits in force between two days ran both of this corner's segments.
     *
     * <p>Both segments on one route is not proof the load turned the corner. A route that runs US
     * 201 the whole way and touches SA 148 at the far end counts here and should not. The office has
     * always accepted that, because the number ranks corners rather than bills anybody.
     */
    public static int turnedAt(EntityManager db, Corner corner, LocalDate start, LocalDate end) {
        List<PermitRow> rows = db.createQuery(
                        "select p from PermitRow p where p.state = :issued and p.validFrom <= :end"
                                + " and p.validTo >= :start",
                        PermitRow.class)
                .setParameter("issued", PermitState.ISSUED)
                .setParameter("end", end)
                .setParameter("start", start)
                .getResultList();
        int found = 0;
        for (PermitRow row : rows) {
            Set<String> idents = Set.copyOf(row.getRouteIdents());
            if (idents.contains(corner.into()) && idents.contains(corner.outOf())) {
                found++;
            }
        }
        return found;
    }

    /** The corners under the tight radius, tightest first, with what turned at each. */
    public static List<Wall> wallList(EntityManager db, TurnBook book, LocalDate start, LocalDate end) {
        return walls(db, book.tight(), start, end);
    }

    /** The corners under a radius of so many feet, tightest first, with what turned at each. */
    public static List<Wall> wallList(EntityManager db, TurnBook book, LocalDate start, LocalDate end, int feet) {
        return walls(db, book.tight(feet), start, end);
    }

    private static List<Wall> walls(EntityManager db, List<Corner> corners, LocalDate start, LocalDate end) {
        return corners.stream()
                .map(one -> new Wall(one, turnedAt(db, one, start, end)))
                .toList();
    }

    /** Every recorded corner on this route, in driven order, with a verdict for this load. */
    public static List<OnRoute> along(Itinerary itinerary, Envelope envelope, TurnBook book) {
        int wanted = Sweep.radiusWanted(envelope);
        Set<Corner> stopped = Sweep.tooTight(itinerary, envelope, book).stream()
                .map(TooTight::corner)
                .collect(Collectors.toSet());
        Set<Corner> near = Sweep.closeRun(itinerary, envelope, book).stream()
                .map(CloseRun::corner)
                .collect(Collectors.toSet());
        List<OnRoute> found = new ArrayList<>();
        for (Corner corner : Sweep.cornersOn(itinerary, book)) {
            String verdict = stopped.contains(corner) ? STOPPED : near.contains(corner) ? CLOSE : CLEAR;
            found.add(new OnRoute(corner, wanted, verdict));
        }
        return List.copyOf(found);
    }

    /** The wall list as lines, which is what the command prints. */
    public static List<String> report(EntityManager db, TurnBook book, LocalDate start, LocalDate end) {
        List<String> lines = new ArrayList<>();
        lines.add("corners on the wall " + start + " to " + end);
        List<Wall> found = wallList(db, book, start, end);
        if (found.isEmpty()) {
            lines.add("nothing on the card index is under the tight radius");
            return List.copyOf(lines);
        }
        found.forEach(one -> lines.add(one.toString()));
        return List.copyOf(lines);
    }

    /** What one load meets on one route, as lines. */
    public static List<String> routeReport(Itinerary itinerary, Envelope envelope, TurnBook book) {
        List<String> lines = new ArrayList<>();
        lines.add("corners on " + itinerary.origin() + " to " + itinerary.destination());
        List<OnRoute> found = along(itinerary, envelope, book);
        if (found.isEmpty()) {
            lines.add("no corner on this route is on the card index");
            return List.copyOf(lines);
        }
        found.forEach(one -> lines.add(one.toString()));
        long stopped =
                found.stream().filter(one -> one.verdict().equals(STOPPED)).count();
        if (stopped > 0) {
            lines.add("this load is stopped at " + stopped + " of them");
        }
        return List.copyOf(lines);
    }
}
