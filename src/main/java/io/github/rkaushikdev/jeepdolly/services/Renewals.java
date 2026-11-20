package io.github.rkaushikdev.jeepdolly.services;

import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.NotAllowed;
import io.github.rkaushikdev.jeepdolly.domain.Carrier;
import io.github.rkaushikdev.jeepdolly.permits.Annual;
import io.github.rkaushikdev.jeepdolly.permits.Application;
import io.github.rkaushikdev.jeepdolly.permits.Office;
import io.github.rkaushikdev.jeepdolly.permits.Permit;
import io.github.rkaushikdev.jeepdolly.permits.PermitState;
import io.github.rkaushikdev.jeepdolly.permits.Series;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.persistence.models.PermitRow;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The renewal run, which is the busiest fortnight the counter has.
 *
 * <p>An annual permit runs a year from the day it was issued, so renewals arrive every week of the
 * year, four hundred of them. This is the list of what is running out, and then the two things
 * nobody could do from a screen. It writes the letters, one per carrier rather than one per permit,
 * because a crane company with nine annuals wants one envelope with nine lines in it. And it renews
 * in a batch: nine new permits in one transaction, each priced off the schedule in force on the day
 * of renewal, each numbered off the counter in order, or none of them, because half a renewal is
 * worse than none.
 *
 * <p>What stops a renewal is the carrier rather than the permit: a suspension, a lapsed certificate
 * or an enforcement hold stops the whole batch, and says which.
 */
public final class Renewals {

    /** How far ahead the run looks when nobody says otherwise. */
    public static final int AHEAD_DAYS = 30;

    /** Who the run is recorded as, when it is run from a schedule rather than a counter. */
    public static final String ACTOR = "renewals";

    private Renewals() {}

    /** One annual permit running out, with what the letter needs. */
    public record Due(String number, int usdot, String carrier, LocalDate expires) {

        public long daysLeft(LocalDate on) {
            return ChronoUnit.DAYS.between(on, expires);
        }

        @Override
        public String toString() {
            return number + "  " + expires + "  " + carrier;
        }
    }

    /** One carrier's renewal letter, however many permits are on it. */
    public record Letter(int usdot, String carrier, List<Due> due) {

        public Letter {
            due = List.copyOf(due);
        }

        public int count() {
            return due.size();
        }

        /** The first of the permits to run out. */
        public LocalDate soonest() {
            return Collections.min(due, Comparator.comparing(Due::expires)).expires();
        }

        /** The letter as it is posted: the carrier, one line per permit, and a closing count. */
        public List<String> lines() {
            List<String> found = new ArrayList<>();
            found.add(carrier + " (" + usdot + ")");
            due.forEach(one -> found.add("  " + one.number() + " expires " + one.expires()));
            found.add("  " + count() + " permits, first on " + soonest());
            return List.copyOf(found);
        }

        @Override
        public String toString() {
            return carrier + ": " + count() + " due by " + soonest();
        }
    }

    /** What a batch renewal produced. */
    public record Renewed(int usdot, List<String> replaced, List<Permit> issued) {

        public Renewed {
            replaced = List.copyOf(replaced);
            issued = List.copyOf(issued);
        }

        public int count() {
            return issued.size();
        }

        @Override
        public String toString() {
            return usdot + ": " + count() + " renewed";
        }
    }

    /** Every live annual running out inside the usual window, soonest first. */
    public static List<Due> dueBy(UnitOfWork work, LocalDate on) {
        return dueBy(work, on, AHEAD_DAYS);
    }

    /** Every live annual running out inside a window of days, soonest first. */
    public static List<Due> dueBy(UnitOfWork work, LocalDate on, int within) {
        return work
                .entityManager()
                .createQuery(
                        "select p from PermitRow p where p.series = :series and p.state = :issued"
                                + " and p.validTo >= :on and p.validTo <= :horizon order by p.validTo, p.number",
                        PermitRow.class)
                .setParameter("series", Series.ANNUAL.value())
                .setParameter("issued", PermitState.ISSUED)
                .setParameter("on", on)
                .setParameter("horizon", on.plusDays(within))
                .getResultList()
                .stream()
                .map(one -> new Due(one.getNumber(), one.getUsdot(), one.getCarrierName(), one.getValidTo()))
                .toList();
    }

    /** One letter per carrier for the usual window, soonest expiry first. */
    public static List<Letter> letters(UnitOfWork work, LocalDate on) {
        return letters(work, on, AHEAD_DAYS);
    }

    /** One letter per carrier, soonest expiry first, however many permits. */
    public static List<Letter> letters(UnitOfWork work, LocalDate on, int within) {
        Map<Integer, List<Due>> grouped = new LinkedHashMap<>();
        Map<Integer, String> names = new LinkedHashMap<>();
        for (Due one : dueBy(work, on, within)) {
            grouped.computeIfAbsent(one.usdot(), usdot -> new ArrayList<>()).add(one);
            names.put(one.usdot(), one.carrier());
        }
        return grouped.entrySet().stream()
                .map(entry -> new Letter(entry.getKey(), names.get(entry.getKey()), entry.getValue()))
                .sorted(Comparator.comparing(Letter::soonest).thenComparingInt(Letter::usdot))
                .toList();
    }

    /** Renew a carrier's annuals in one transaction, recorded as the scheduled run. */
    public static Renewed renew(UnitOfWork work, Office office, int usdot, List<String> numbers, LocalDate on) {
        return renew(work, office, usdot, numbers, on, ACTOR);
    }

    /**
     * Renew a carrier's annuals in one transaction, or none of them.
     *
     * <p>The old permits are left to run out rather than superseded. A superseded permit is one that
     * was replaced because it was wrong, and these were not wrong: they were right for a year and the
     * year is ending.
     *
     * @throws NotAllowed when nothing is named, the carrier is stopped or held, or a permit is not
     *     the carrier's own live permit
     */
    public static Renewed renew(
            UnitOfWork work, Office office, int usdot, List<String> numbers, LocalDate on, String who) {
        if (numbers.isEmpty()) {
            throw new NotAllowed("a renewal names at least one permit");
        }
        Carrier carrier = office.carriers().get(usdot);
        Optional<String> standing = carrier.standing(on, on, 0);
        if (standing.isPresent()) {
            throw new NotAllowed(usdot + " cannot renew: " + standing.get(), Facts.of("usdot", usdot));
        }
        Optional<String> hold = office.enforcement().hold(usdot, on);
        if (hold.isPresent()) {
            throw new NotAllowed(usdot + " cannot renew: " + hold.get(), Facts.of("usdot", usdot));
        }
        List<Permit> made = new ArrayList<>();
        for (String number : numbers) {
            Permit old = work.permits().get(number);
            if (old.usdot() != usdot) {
                throw new NotAllowed(
                        number + " does not belong to " + usdot, Facts.of("permit", number, "usdot", usdot));
            }
            if (old.state() != PermitState.ISSUED) {
                throw new NotAllowed(
                        number + " is " + old.state() + " and is not renewable", Facts.of("permit", number));
            }
            made.add(one(work, office, old, on));
        }
        Audit.record(
                work.entityManager(),
                "permit.renewed",
                who,
                "carrier",
                String.valueOf(usdot),
                made.size() + " annuals");
        return new Renewed(usdot, numbers, made);
    }

    /**
     * Issue one replacement annual off today's schedule and counter.
     *
     * <p>The commodity is read off the application the old permit was written from. That is the one
     * thing a permit does not carry: the paper says what is allowed rather than what is being moved,
     * and the annual eligibility rules want the commodity.
     */
    private static Permit one(UnitOfWork work, Office office, Permit old, LocalDate on) {
        Application application = work.applications().get(old.application());
        Permit made = Annual.issue(
                office.numbering(),
                office.library(),
                office.schedules().on(on),
                old.application(),
                old.usdot(),
                old.carrierName(),
                old.envelope(),
                old.gross(),
                office.commodities().get(application.commodity()),
                on);
        work.permits().add(made);
        return made;
    }
}
