package io.github.rkaushikdev.jeepdolly.services;

import io.github.rkaushikdev.jeepdolly.permits.ApplicationState;
import io.github.rkaushikdev.jeepdolly.permits.PermitState;
import io.github.rkaushikdev.jeepdolly.structures.ReviewState;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Numbers about the office, counted off the database rather than in memory.
 *
 * <p>Everything here is a query. There is no counter incremented in a process, and that is
 * deliberate: the service runs behind more than one worker, workers are restarted, and a count that
 * lives in a process is a count that disagrees with itself between two scrapes. A scrape is a
 * handful of counting queries against tables already indexed for the reports, which costs a few
 * milliseconds and is always right.
 *
 * <p>The names are flat and boring on purpose. Whatever collects them graphs them next to a hundred
 * other services, and the graph wants a name it can autocomplete, not a sentence.
 */
public final class Metrics {

    /** What every published name starts with. */
    public static final String PREFIX = "jeepdolly";

    private static final List<ReviewState> OPEN =
            List.of(ReviewState.WAITING, ReviewState.WITH_ENGINEER, ReviewState.HELD);

    private Metrics() {}

    /** One number, its name, and one line of what it means. */
    public record Measure(String name, long value, String about) {

        /** The help line, the type line and the sample, in the scrape's text format. */
        public List<String> lines() {
            String full = PREFIX + "_" + name;
            return List.of("# HELP " + full + " " + about, "# TYPE " + full + " gauge", full + " " + value);
        }

        @Override
        public String toString() {
            return PREFIX + "_" + name + " " + value;
        }
    }

    private static long count(EntityManager db, String query, Map<String, Object> parameters) {
        TypedQuery<Long> typed = db.createQuery(query, Long.class);
        parameters.forEach(typed::setParameter);
        Long found = typed.getSingleResult();
        return found == null ? 0 : found;
    }

    /** Every number the office publishes, counted now. */
    public static List<Measure> snapshot(EntityManager db, LocalDate day) {
        LocalDate week = day.minusDays(7);
        List<Measure> found = new ArrayList<>();
        found.add(new Measure(
                "permits_on_the_road",
                count(
                        db,
                        "select count(p) from PermitRow p where p.state = :issued"
                                + " and p.validFrom <= :day and p.validTo >= :day",
                        Map.of("issued", PermitState.ISSUED, "day", day)),
                "permits valid for travel today"));
        found.add(new Measure(
                "permits_issued_week",
                count(
                        db,
                        "select count(p) from PermitRow p where p.issued >= :week and p.issued <= :day",
                        Map.of("week", week, "day", day)),
                "permits written in the last seven days"));
        found.add(new Measure(
                "permits_void",
                count(db, "select count(p) from PermitRow p where p.state = :state", Map.of("state", PermitState.VOID)),
                "permits voided since the beginning"));
        found.add(new Measure(
                "applications_waiting",
                count(
                        db,
                        "select count(a) from ApplicationRow a where a.state = :state",
                        Map.of("state", ApplicationState.SUBMITTED)),
                "applications submitted and not yet decided"));
        found.add(new Measure(
                "applications_draft",
                count(
                        db,
                        "select count(a) from ApplicationRow a where a.state = :state",
                        Map.of("state", ApplicationState.DRAFT)),
                "applications typed and not submitted"));
        found.add(new Measure(
                "studies_open",
                count(db, "select count(r) from ReviewRow r where r.state in :open", Map.of("open", OPEN)),
                "studies that have not been decided"));
        found.add(new Measure(
                "segments", count(db, "select count(s) from SegmentRow s", Map.of()), "segments on the road log"));
        found.add(new Measure(
                "restrictions",
                count(db, "select count(r) from RestrictionRow r", Map.of()),
                "restrictions on file, in force or not"));
        found.add(new Measure(
                "structures_posted",
                count(db, "select count(s) from StructureRow s where s.posted is not null", Map.of()),
                "structures carrying a posted weight"));
        found.add(new Measure(
                "carriers",
                count(db, "select count(c) from CarrierRow c", Map.of()),
                "carriers the office has ever written for"));
        return List.copyOf(found);
    }

    /** The scrape, in the text format every collector reads, ending in a newline. */
    public static String exposition(EntityManager db, LocalDate day) {
        List<String> lines = new ArrayList<>();
        snapshot(db, day).forEach(measure -> lines.addAll(measure.lines()));
        return String.join("\n", lines) + "\n";
    }

    /** The same numbers by name, in publishing order, for a test or a console. */
    public static Map<String, Long> byName(EntityManager db, LocalDate day) {
        Map<String, Long> found = new LinkedHashMap<>();
        snapshot(db, day).forEach(one -> found.put(one.name(), one.value()));
        return found;
    }
}
