package io.github.rkaushikdev.jeepdolly.reports;

import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.persistence.models.LineCrossingRow;
import io.github.rkaushikdev.jeepdolly.persistence.repositories.LineRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * The line survey: what is low, what is old, and what the office cannot lift.
 *
 * <p>The survey runs on a five year cycle and the crew that runs it has one van, so the question is
 * never whether to remeasure everything. It is which two hundred spans to look at this summer.
 * Lowest first is the obvious answer and the least useful, because the wires at eighteen feet are
 * already on every card. What matters is low and old together: a span measured in 1998 at nineteen
 * feet may be at seventeen now, and nobody would know.
 *
 * <p>The other list is the wires that cannot be lifted. Those decide routes rather than delay them,
 * and when one of them is remeasured higher than it was, a route opens up that has been refused for
 * a decade. A crossing with no measured day is treated as the oldest thing on the list: it came off
 * a paper card somebody keyed in, and it is older than anything with a date on it.
 */
public final class LineSurvey {

    /** How long a measurement stands before the office wants it walked again. */
    public static final int CYCLE_YEARS = 5;

    /** What counts as a low wire when nobody says otherwise. */
    public static final Inches LOW = Inches.of(20);

    private LineSurvey() {}

    /** One crossing as the survey list reads it. */
    public record Span(
            String ident,
            String segment,
            String place,
            Inches height,
            String owner,
            Optional<LocalDate> measured,
            boolean liftable) {

        /** Days since it was walked, or nothing for a span that never was. */
        public OptionalLong ageDays(LocalDate on) {
            return measured.map(day -> OptionalLong.of(ChronoUnit.DAYS.between(day, on)))
                    .orElse(OptionalLong.empty());
        }

        /** Whether the measurement is out of the usual cycle on a day. */
        public boolean staleOn(LocalDate day) {
            return staleOn(day, CYCLE_YEARS);
        }

        /** Whether the measurement is older than a cycle of so many years, a year being 365 days. */
        public boolean staleOn(LocalDate day, int years) {
            return measured.map(one -> one.isBefore(day.minusDays(365L * years)))
                    .orElse(true);
        }

        @Override
        public String toString() {
            String when = measured.map(LocalDate::toString).orElse("never");
            String lift = liftable ? "" : "  fixed";
            return ident + "  " + segment + " at " + place + "  " + height + "  " + when + lift;
        }
    }

    /** Every crossing on file, lowest first. */
    public static List<Span> spans(EntityManager db) {
        return sorted(new LineRepository(db).crossings());
    }

    /** The crossings under a height, lowest first. */
    public static List<Span> spans(EntityManager db, Inches under) {
        return sorted(new LineRepository(db).under(under));
    }

    private static List<Span> sorted(List<LineCrossingRow> rows) {
        return rows.stream()
                .map(LineSurvey::span)
                .sorted(Comparator.comparingInt((Span one) -> one.height().value())
                        .thenComparing(Span::ident))
                .toList();
    }

    private static Span span(LineCrossingRow row) {
        return new Span(
                row.getIdent(),
                row.getSegmentIdent(),
                row.getPlace(),
                row.getHeight(),
                row.getOwnerIdent(),
                row.getMeasured(),
                row.getLiftTo().isPresent());
    }

    /** The van's list for the usual height and cycle. */
    public static List<Span> overdue(EntityManager db, LocalDate day) {
        return overdue(db, day, LOW, CYCLE_YEARS);
    }

    /**
     * The van's list: low wires nobody has walked inside the cycle.
     *
     * <p>Ordered by height rather than by age. A very old measurement of a wire at twenty six feet
     * is not urgent, because nothing the office permits is twenty six feet, and the crew's day is
     * better spent under the low ones.
     */
    public static List<Span> overdue(EntityManager db, LocalDate day, Inches under, int years) {
        return spans(db, under).stream().filter(one -> one.staleOn(day, years)).toList();
    }

    /** Wires nothing can lift, which are the ones that decide routes. */
    public static List<Span> fixed(EntityManager db) {
        return spans(db).stream().filter(one -> !one.liftable()).toList();
    }

    /** The survey list for the usual height, as the command prints it. */
    public static List<String> report(EntityManager db, LocalDate day) {
        return report(db, day, LOW);
    }

    /** The survey list as lines, which is what the command prints. */
    public static List<String> report(EntityManager db, LocalDate day, Inches under) {
        List<String> lines = new ArrayList<>();
        lines.add("line survey " + day + ", wires under " + under);
        List<Span> walk = overdue(db, day, under, CYCLE_YEARS);
        if (walk.isEmpty()) {
            lines.add("nothing low is out of cycle");
        } else {
            walk.forEach(one -> lines.add(one.toString()));
        }
        List<Span> standing = fixed(db);
        if (!standing.isEmpty()) {
            lines.add("wires that cannot be lifted");
            standing.forEach(one -> lines.add("  " + one));
        }
        return List.copyOf(lines);
    }
}
