package io.github.rkaushikdev.jeepdolly.reports;

import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.permits.PermitState;
import io.github.rkaushikdev.jeepdolly.persistence.models.PermitRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.StructureRow;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * What went over each structure, which nobody asked for until they did.
 *
 * <p>A bridge is inspected on a cycle and rated on what it carries, and the rating is an estimate of
 * the traffic. The office holds the one record in the state that says what actually crossed: every
 * permit names its route, every route names its segments, and every segment carries its structures.
 * So this walks the permits and counts them onto the structures. It is the report the bridge people
 * ring about after a joint fails, and the heaviest crossing is the number they want: not the count,
 * not the average, the single heaviest load permitted over it and the day it went.
 *
 * <p>A voided permit is not counted, and that is the one place in the reports where a void changes
 * an answer. The money was taken whether or not the load moved, but the bridge did not feel a load
 * that never went.
 */
public final class StructureReport {

    /** How many structures the busiest list names when nobody says otherwise. */
    public static final int BUSIEST = 10;

    private StructureReport() {}

    /** One permit over one structure, on the first day it was good for. */
    public record Crossing(String permit, LocalDate day, Pounds gross, String carrier) {

        @Override
        public String toString() {
            return permit + "  " + day + "  " + gross + "  " + carrier;
        }
    }

    /** One structure and everything that was permitted over it. */
    public record Hits(String ident, String name, String segment, Optional<Pounds> posted, List<Crossing> crossings) {

        public Hits {
            crossings = List.copyOf(crossings);
        }

        public int count() {
            return crossings.size();
        }

        /** The heaviest load permitted over it, the first of them on a tie. */
        public Optional<Crossing> heaviest() {
            return crossings.stream()
                    .max(Comparator.comparingInt(one -> one.gross().value()));
        }

        /** Anything permitted over a posted weight, which should be nothing. */
        public List<Crossing> overPosting() {
            return posted.map(limit -> crossings.stream()
                            .filter(one -> one.gross().compareTo(limit) > 0)
                            .toList())
                    .orElse(List.of());
        }

        /** The structure's lines: its count, the heaviest crossing, and any crossing over the sign. */
        public List<String> lines() {
            List<String> found = new ArrayList<>();
            found.add(ident + "  " + name + "  " + count() + " crossings");
            heaviest().ifPresent(one -> found.add("  heaviest  " + one));
            overPosting().forEach(one -> found.add("  OVER THE POSTING  " + one));
            return List.copyOf(found);
        }

        @Override
        public String toString() {
            return ident + " " + name + ", " + count() + " crossings";
        }
    }

    /** Every structure, by ident, with what crossed it between two days. */
    public static List<Hits> crossings(EntityManager db, LocalDate start, LocalDate end) {
        List<PermitRow> permits = db.createQuery(
                        "select p from PermitRow p where p.state = :issued and p.validFrom <= :end"
                                + " and p.validTo >= :start order by p.number",
                        PermitRow.class)
                .setParameter("issued", PermitState.ISSUED)
                .setParameter("end", end)
                .setParameter("start", start)
                .getResultList();
        List<Hits> found = new ArrayList<>();
        for (StructureRow structure : db.createQuery(
                        "select s from StructureRow s order by s.ident", StructureRow.class)
                .getResultList()) {
            List<Crossing> over = permits.stream()
                    .filter(one -> one.getRouteIdents().contains(structure.getSegmentIdent()))
                    .map(one -> new Crossing(one.getNumber(), one.getValidFrom(), one.getGross(), one.getCarrierName()))
                    .toList();
            found.add(new Hits(
                    structure.getIdent(),
                    structure.getName(),
                    structure.getSegmentIdent(),
                    structure.getPosted(),
                    over));
        }
        return List.copyOf(found);
    }

    /** The ten structures that carried the most, which is where inspection goes. */
    public static List<Hits> busiest(EntityManager db, LocalDate start, LocalDate end) {
        return busiest(db, start, end, BUSIEST);
    }

    /** The structures that carried the most, most first, nothing that carried nothing. */
    public static List<Hits> busiest(EntityManager db, LocalDate start, LocalDate end, int limit) {
        return crossings(db, start, end).stream()
                .filter(one -> one.count() > 0)
                .sorted(Comparator.comparingInt(Hits::count).reversed())
                .limit(limit)
                .toList();
    }

    /** Structures a permit was written over in spite of the sign. */
    public static List<Hits> anythingOverAPosting(EntityManager db, LocalDate start, LocalDate end) {
        return crossings(db, start, end).stream()
                .filter(one -> !one.overPosting().isEmpty())
                .toList();
    }

    /** The whole thing as lines, quiet structures left out. */
    public static List<String> report(EntityManager db, LocalDate start, LocalDate end) {
        List<String> lines = new ArrayList<>();
        lines.add("structure crossings " + start + " to " + end);
        crossings(db, start, end).stream().filter(one -> one.count() > 0).forEach(one -> lines.addAll(one.lines()));
        if (lines.size() == 1) {
            lines.add("nothing crossed anything");
        }
        return List.copyOf(lines);
    }
}
