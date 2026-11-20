package io.github.rkaushikdev.jeepdolly.reports;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.domain.RestrictionKind;
import io.github.rkaushikdev.jeepdolly.persistence.models.RestrictionRow;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * What is wrong with the road, day by day, for the fortnight ahead.
 *
 * <p>The counter needs one page that says which roads are shut this week and which come back next
 * week, because the question arrives as "can I get to Anson on Thursday" and the answer lives in
 * three tables. Two shapes come out of here. The calendar is by day: for each day in a span, what
 * came into force that was not in force the day before, and what lifted, because what people want
 * to know is what changed. The standing list is by segment: everything in force on one day, in road
 * log order, which is what gets faxed to the state police on a Monday morning.
 *
 * <p>Nothing here reads a load. A restriction is on the calendar whether or not it is in the way of
 * anything, because the person reading it is deciding what to tell a hauler who has not rung yet.
 */
public final class RoadCalendar {

    /** How many days a calendar covers when nobody says otherwise. */
    public static final int FORTNIGHT = 14;

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    private RoadCalendar() {}

    /**
     * One restriction as it appears on the list.
     *
     * @param kind the kind's stored value, such as {@code closure}
     * @param limit the number the kind carries, as it prints, or empty
     */
    public record Standing(
            String ident,
            String segment,
            String kind,
            String note,
            String limit,
            Optional<LocalDate> fromDay,
            Optional<LocalDate> toDay) {

        /** No dates at all. */
        public boolean permanent() {
            return fromDay.isEmpty() && toDay.isEmpty();
        }

        @Override
        public String toString() {
            String span = permanent() ? "permanent" : written(fromDay) + " to " + written(toDay);
            return segment + "  " + kind + "  " + limit + "  " + span + "  " + note;
        }
    }

    /** One day of the calendar, and what changed on it. */
    public record Day(LocalDate day, List<Standing> started, List<Standing> lifted) {

        public Day {
            started = List.copyOf(started);
            lifted = List.copyOf(lifted);
        }

        /** Nothing came into force and nothing lifted. */
        public boolean quiet() {
            return started.isEmpty() && lifted.isEmpty();
        }

        /** One line per change, closures before openings. */
        public List<String> lines() {
            List<String> found = new ArrayList<>();
            started.forEach(one -> found.add(day + "  closes  " + one));
            lifted.forEach(one -> found.add(day + "  opens   " + one));
            return List.copyOf(found);
        }
    }

    private static String written(Optional<LocalDate> day) {
        return day.map(LocalDate::toString).orElse("further notice");
    }

    private static String limitOf(RestrictionRow row) {
        RestrictionKind kind = row.getKind();
        if (kind == RestrictionKind.CLEARANCE && row.getHeight().isPresent()) {
            return row.getHeight().get().toString();
        }
        if (kind == RestrictionKind.WIDTH && row.getWidth().isPresent()) {
            return row.getWidth().get().toString();
        }
        if (kind == RestrictionKind.WEIGHT && row.getWeight().isPresent()) {
            return row.getWeight().get().toString();
        }
        if (kind == RestrictionKind.SEASONAL) {
            return row.getRegion().orElse("");
        }
        Optional<LocalTime> from = row.getCurfewFrom();
        Optional<LocalTime> to = row.getCurfewTo();
        if (kind == RestrictionKind.CURFEW && from.isPresent() && to.isPresent()) {
            return CLOCK.format(from.get()) + " to " + CLOCK.format(to.get());
        }
        return "";
    }

    private static Standing standing(RestrictionRow row) {
        return new Standing(
                row.getIdent(),
                row.getSegmentIdent(),
                row.getKind().value(),
                row.getNote(),
                limitOf(row),
                row.getEffective(),
                row.getExpires());
    }

    private static boolean inForce(RestrictionRow row, LocalDate day) {
        if (row.getEffective().isPresent() && day.isBefore(row.getEffective().get())) {
            return false;
        }
        return row.getExpires().isEmpty() || !day.isAfter(row.getExpires().get());
    }

    private static List<RestrictionRow> everything(EntityManager db) {
        return db.createQuery("select r from RestrictionRow r order by r.segmentIdent, r.ident", RestrictionRow.class)
                .getResultList();
    }

    /** Everything in force on one day, in road log order. */
    public static List<Standing> standingOn(EntityManager db, LocalDate day) {
        return everything(db).stream()
                .filter(one -> inForce(one, day))
                .map(RoadCalendar::standing)
                .toList();
    }

    /** What changes each day over the fortnight from a day. */
    public static List<Day> calendar(EntityManager db, LocalDate start) {
        return calendar(db, start, FORTNIGHT);
    }

    /**
     * What changes each day over a span, which is what gets pinned up.
     *
     * @throws Impossible for a calendar of no days
     */
    public static List<Day> calendar(EntityManager db, LocalDate start, int days) {
        if (days < 1) {
            throw new Impossible("a calendar covers at least one day");
        }
        List<RestrictionRow> rows = everything(db);
        List<Day> found = new ArrayList<>();
        for (int step = 0; step < days; step++) {
            LocalDate day = start.plusDays(step);
            LocalDate before = day.minusDays(1);
            List<Standing> started = rows.stream()
                    .filter(one -> inForce(one, day) && !inForce(one, before))
                    .map(RoadCalendar::standing)
                    .toList();
            List<Standing> lifted = rows.stream()
                    .filter(one -> inForce(one, before) && !inForce(one, day))
                    .map(RoadCalendar::standing)
                    .toList();
            found.add(new Day(day, started, lifted));
        }
        return List.copyOf(found);
    }

    /** The fortnight's changes as lines. */
    public static List<String> changes(EntityManager db, LocalDate start) {
        return changes(db, start, FORTNIGHT);
    }

    /** The calendar as lines, with the quiet days left out. */
    public static List<String> changes(EntityManager db, LocalDate start, int days) {
        List<String> found = new ArrayList<>();
        found.add("road changes " + start + " for " + days + " days");
        calendar(db, start, days).forEach(one -> found.addAll(one.lines()));
        if (found.size() == 1) {
            found.add("nothing changes");
        }
        return List.copyOf(found);
    }
}
