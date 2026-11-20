package io.github.rkaushikdev.jeepdolly.reports;

import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.persistence.models.FeeLineRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.PermitRow;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Where the money came from, in the three cuts the office is asked for.
 *
 * <p>By kind of charge, which is what the department wants: how much of last year was base fees and
 * how much was weight, grouped here by what each fee line says. By carrier, which is what a carrier
 * asks for when it reconciles its own account. By month, which is what the budget is written
 * against.
 *
 * <p>Every figure is the sum of what was printed. A permit that was voided is still counted,
 * because the money was taken and voiding a permit does not refund it. That is the office rule and
 * it is the one people query, so it is stated on the report rather than left to be discovered.
 */
public final class RevenueReport {

    /** A word a fee line description carries, and the kind of charge it makes the line. */
    public record Kind(String needle, String name) {}

    /** How a fee line description is grouped into a kind of charge, first match winning. */
    public static final List<Kind> KINDS = List.of(
            new Kind("permit", "base fee"),
            new Kind("miles", "mileage"),
            new Kind("over legal gross", "weight"),
            new Kind("engineering review", "review"),
            new Kind("minimum", "minimum"),
            new Kind("amendment", "amendment"));

    private RevenueReport() {}

    /** Which kind of charge a fee line falls in, by what it says; {@code other} when nothing matches. */
    public static String kindOf(String description) {
        String lowered = description.toLowerCase(Locale.ROOT);
        return KINDS.stream()
                .filter(kind -> lowered.contains(kind.needle()))
                .map(Kind::name)
                .findFirst()
                .orElse("other");
    }

    /**
     * What was taken over a span, cut three ways.
     *
     * @param byMonth keyed {@code 2025-04}
     */
    public record Revenue(
            LocalDate start,
            LocalDate end,
            Map<String, Dollars> byKind,
            Map<String, Dollars> byCarrier,
            Map<String, Dollars> byMonth,
            int permits) {

        public Revenue {
            byKind = Collections.unmodifiableMap(new LinkedHashMap<>(byKind));
            byCarrier = Collections.unmodifiableMap(new LinkedHashMap<>(byCarrier));
            byMonth = Collections.unmodifiableMap(new LinkedHashMap<>(byMonth));
        }

        public Dollars taken() {
            return Dollars.total(byKind.values());
        }

        /** What one kind of charge took, which is nothing for a kind nobody charged. */
        public Dollars kind(String name) {
            return byKind.getOrDefault(name, Dollars.ZERO);
        }

        /** The report as it prints, kinds in name order and the voiding rule stated. */
        public List<String> lines() {
            List<String> found = new ArrayList<>();
            found.add("revenue " + start + " to " + end);
            byKind.keySet().stream().sorted().forEach(name -> found.add(name + "  " + byKind.get(name)));
            found.add(permits + " permits  " + taken());
            found.add("voided permits are counted, because the money was taken");
            return List.copyOf(found);
        }

        @Override
        public String toString() {
            return permits + " permits, " + taken();
        }
    }

    /**
     * Add up the fee lines of every permit issued in a span.
     *
     * @throws Impossible when the span ends before it starts
     */
    public static Revenue revenue(EntityManager db, LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            throw new Impossible("a report ends after it starts");
        }
        Map<String, Dollars> byKind = new LinkedHashMap<>();
        Map<String, Dollars> byCarrier = new LinkedHashMap<>();
        Map<String, Dollars> byMonth = new LinkedHashMap<>();
        int permits = 0;
        for (PermitRow row : db.createQuery(
                        "select p from PermitRow p where p.issued >= :start and p.issued <= :end"
                                + " order by p.issued, p.number",
                        PermitRow.class)
                .setParameter("start", start)
                .setParameter("end", end)
                .getResultList()) {
            permits++;
            String month = String.format(
                    Locale.ROOT,
                    "%04d-%02d",
                    row.getIssued().getYear(),
                    row.getIssued().getMonthValue());
            for (FeeLineRow line : db.createQuery(
                            "select f from FeeLineRow f where f.number = :number", FeeLineRow.class)
                    .setParameter("number", row.getNumber())
                    .getResultList()) {
                byKind.merge(kindOf(line.getDescription()), line.getAmount(), Dollars::plus);
                byCarrier.merge(row.getCarrierName(), line.getAmount(), Dollars::plus);
                byMonth.merge(month, line.getAmount(), Dollars::plus);
            }
        }
        return new Revenue(start, end, byKind, byCarrier, byMonth, permits);
    }

    /** What one carrier was charged over a span, for its own reconciliation. */
    public static Dollars owedBy(EntityManager db, String carrierName, LocalDate start, LocalDate end) {
        return revenue(db, start, end).byCarrier().getOrDefault(carrierName, Dollars.ZERO);
    }
}
