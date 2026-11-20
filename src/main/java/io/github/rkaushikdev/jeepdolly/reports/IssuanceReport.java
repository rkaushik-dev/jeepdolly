package io.github.rkaushikdev.jeepdolly.reports;

import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.permits.ApplicationState;
import io.github.rkaushikdev.jeepdolly.persistence.models.FeeLineRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.PermitRow;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the office wrote, and what it took, for a day or a span.
 *
 * <p>This is the report printed every evening and initialled, so its shape is fixed by what
 * somebody signs. The count by series says what kind of work the day was: forty single trips and
 * one superload is a normal Tuesday. The money says what to reconcile against the till, and it is
 * the sum of the fee lines as they were printed, not a recomputation from the schedule, because the
 * till has what was charged and not what should have been. The refusals say what the office turned
 * away, which nobody asks for until the year the legislature wants five years of it.
 */
public final class IssuanceReport {

    private IssuanceReport() {}

    /** One permit as it appears on the report. */
    public record Issued(String number, String series, String carrier, String gross, String destination, Dollars fee) {

        @Override
        public String toString() {
            return number + "  " + carrier + "  " + gross + "  " + fee;
        }
    }

    /**
     * The day, or the span, as it is signed off.
     *
     * @param bySeries how many permits of each series letter, in the order they were written
     */
    public record Issuance(
            LocalDate start,
            LocalDate end,
            List<Issued> permits,
            int refused,
            int withdrawn,
            Map<String, Integer> bySeries) {

        public Issuance {
            permits = List.copyOf(permits);
            bySeries = Collections.unmodifiableMap(new LinkedHashMap<>(bySeries));
        }

        public int count() {
            return permits.size();
        }

        /** What the permits printed, which is what the till should hold. */
        public Dollars taken() {
            return Dollars.total(permits.stream().map(Issued::fee).toList());
        }

        public boolean oneDay() {
            return start.equals(end);
        }

        /** The permits of one series letter, in the order they were written. */
        public List<Issued> ofSeries(String series) {
            return permits.stream().filter(one -> one.series().equals(series)).toList();
        }

        public String heading() {
            if (oneDay()) {
                return "permits issued " + start;
            }
            return "permits issued " + start + " to " + end;
        }

        /** The report as it prints, heading and footing included. */
        public List<String> lines() {
            List<String> found = new ArrayList<>();
            found.add(heading());
            permits.forEach(one -> found.add(one.toString()));
            found.add(count() + " permits  " + taken());
            if (refused != 0 || withdrawn != 0) {
                found.add(refused + " refused  " + withdrawn + " withdrawn");
            }
            return List.copyOf(found);
        }

        @Override
        public String toString() {
            return count() + " permits, " + taken();
        }
    }

    private static Dollars feeFor(EntityManager db, String number) {
        return Dollars.total(db
                .createQuery("select f from FeeLineRow f where f.number = :number", FeeLineRow.class)
                .setParameter("number", number)
                .getResultList()
                .stream()
                .map(FeeLineRow::getAmount)
                .toList());
    }

    /** Everything written on one day. */
    public static Issuance issuance(EntityManager db, LocalDate day) {
        return issuance(db, day, day);
    }

    /**
     * Everything written between two days, inclusive of both.
     *
     * @throws Impossible when the span ends before it starts
     */
    public static Issuance issuance(EntityManager db, LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            throw new Impossible("a report ends after it starts");
        }
        List<Issued> permits = new ArrayList<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (PermitRow row : db.createQuery(
                        "select p from PermitRow p where p.issued >= :start and p.issued <= :end"
                                + " order by p.issued, p.number",
                        PermitRow.class)
                .setParameter("start", start)
                .setParameter("end", end)
                .getResultList()) {
            permits.add(new Issued(
                    row.getNumber(),
                    row.getSeries(),
                    row.getCarrierName(),
                    row.getGross().toString(),
                    row.getDestination(),
                    feeFor(db, row.getNumber())));
            counts.merge(row.getSeries(), 1, Integer::sum);
        }
        return new Issuance(
                start,
                end,
                permits,
                applications(db, start, end, ApplicationState.REFUSED),
                applications(db, start, end, ApplicationState.WITHDRAWN),
                counts);
    }

    private static int applications(EntityManager db, LocalDate start, LocalDate end, ApplicationState state) {
        return db.createQuery(
                        "select count(a) from ApplicationRow a where a.filed >= :start and a.filed <= :end"
                                + " and a.state = :state",
                        Long.class)
                .setParameter("start", start)
                .setParameter("end", end)
                .setParameter("state", state)
                .getSingleResult()
                .intValue();
    }
}
