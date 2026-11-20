package io.github.rkaushikdev.jeepdolly.services;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.enforcement.Account;
import io.github.rkaushikdev.jeepdolly.enforcement.EnforcementBook;
import io.github.rkaushikdev.jeepdolly.permits.ApplicationState;
import io.github.rkaushikdev.jeepdolly.persistence.Books;
import io.github.rkaushikdev.jeepdolly.persistence.models.ApplicationRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.CarrierRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.InsuranceRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.PermitRow;
import io.github.rkaushikdev.jeepdolly.persistence.repositories.PermitRepository;
import io.github.rkaushikdev.jeepdolly.persistence.repositories.PilotRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The nightly sweep, which is the only thing that runs without a person.
 *
 * <p>Six passes, in this order, each reading what the one before it left. Expiring permits are
 * noticed and nothing is changed: expiry is a fact about the calendar, and the list is what the
 * office rings about in the morning. Applications never submitted are withdrawn after a fortnight,
 * because a draft untouched since the hauler rang off is not going to be submitted. Studies waiting
 * too long are counted, not moved: an engineer decides what happens to a study, not a cron job.
 * Carriers whose insurance lapses inside the week are listed, because the office would rather ring
 * them than refuse them on the Thursday. Pilot car cards running out inside the month are listed for
 * the same reason. Carriers newly held over an unpaid citation are last, and only the ones that
 * crossed the line since the day before, because a carrier held yesterday does not want a second
 * letter.
 *
 * <p>The sweep writes to the trail as one actor called {@code nightly}, so a supervisor can tell
 * what a person did from what the machine did.
 */
public final class Nightly {

    /** Who the sweep is on the trail. */
    public static final String ACTOR = "nightly";

    /** A draft nobody submitted is withdrawn after this many days. */
    public static final int STALE_DRAFT_DAYS = 14;

    /** A study waiting longer than this is on the supervisor's list. */
    public static final int SLOW_STUDY_DAYS = 14;

    /** Insurance lapsing inside this many days is worth a telephone call. */
    public static final int LAPSE_WARNING_DAYS = 7;

    /** A pilot car card running out inside this many days is worth a letter. */
    public static final int CARD_WARNING_DAYS = 30;

    private Nightly() {}

    /** What the night found, and what it did about it. */
    public static final class Sweep {

        private final LocalDate day;
        private final List<String> expiring = new ArrayList<>();
        private final List<String> withdrawn = new ArrayList<>();
        private final List<String> slowStudies = new ArrayList<>();
        private final List<String> lapsing = new ArrayList<>();
        private final List<String> cards = new ArrayList<>();
        private final List<String> held = new ArrayList<>();

        Sweep(LocalDate day) {
            this.day = day;
        }

        public LocalDate day() {
            return day;
        }

        /** Live permits running out inside the window, soonest first. */
        public List<String> expiring() {
            return Collections.unmodifiableList(expiring);
        }

        /** The stale drafts the night withdrew. */
        public List<String> withdrawn() {
            return Collections.unmodifiableList(withdrawn);
        }

        /** Studies that have waited too long, oldest first. */
        public List<String> slowStudies() {
            return Collections.unmodifiableList(slowStudies);
        }

        /** Carriers whose insurance runs out inside the week, by name. */
        public List<String> lapsing() {
            return Collections.unmodifiableList(lapsing);
        }

        /** Pilot car cards running out inside the month, card and name. */
        public List<String> cards() {
            return Collections.unmodifiableList(cards);
        }

        /** Carriers newly held over unpaid citations, worst first. */
        public List<String> held() {
            return Collections.unmodifiableList(held);
        }

        /** How many records the night changed, which is only the withdrawn drafts. */
        public int changed() {
            return withdrawn.size();
        }

        /** The sweep's report: a heading, one line per finding, and a footing of counts. */
        public List<String> lines() {
            List<String> found = new ArrayList<>();
            found.add("nightly " + day);
            expiring.forEach(number -> found.add("expiring  " + number));
            withdrawn.forEach(reference -> found.add("withdrawn  " + reference));
            slowStudies.forEach(reference -> found.add("slow study  " + reference));
            lapsing.forEach(name -> found.add("insurance lapsing  " + name));
            cards.forEach(card -> found.add("card lapsing  " + card));
            held.forEach(name -> found.add("held  " + name));
            found.add(expiring.size() + " expiring  " + withdrawn.size() + " withdrawn  " + slowStudies.size()
                    + " slow  " + lapsing.size() + " lapsing  " + cards.size() + " cards  " + held.size()
                    + " held");
            return List.copyOf(found);
        }

        @Override
        public String toString() {
            return "nightly " + day + ", " + changed() + " changed";
        }
    }

    /** Run the six passes, noticing permits that run out in the next two days. */
    public static Sweep sweep(EntityManager db, LocalDate day) {
        return sweep(db, day, 2);
    }

    /**
     * Run the six passes and report what each one found.
     *
     * @throws Impossible when asked to look backwards
     */
    public static Sweep sweep(EntityManager db, LocalDate day, int expiringWithin) {
        if (expiringWithin < 0) {
            throw new Impossible("a sweep looks forward, not back");
        }
        Sweep found = new Sweep(day);
        new PermitRepository(db)
                .expiring(day, day.plusDays(expiringWithin)).stream()
                        .map(PermitRow::getNumber)
                        .forEach(found.expiring::add);
        for (ApplicationRow row : staleDrafts(db, day, STALE_DRAFT_DAYS)) {
            row.setState(ApplicationState.WITHDRAWN);
            Audit.record(
                    db,
                    "application.withdrawn",
                    ACTOR,
                    "application",
                    row.getReference(),
                    "draft untouched since " + row.getFiled());
            found.withdrawn.add(row.getReference());
        }
        Studies.overdue(db, day, SLOW_STUDY_DAYS).forEach(one -> found.slowStudies.add(one.getReference()));
        found.lapsing.addAll(lapsing(db, day, LAPSE_WARNING_DAYS));
        new PilotRepository(db)
                .expiring(day, day.plusDays(CARD_WARNING_DAYS))
                .forEach(one -> found.cards.add(one.getCard() + " " + one.getName()));
        found.held.addAll(newlyHeld(db, day));
        return found;
    }

    private static List<ApplicationRow> staleDrafts(EntityManager db, LocalDate day, int olderThan) {
        return db.createQuery(
                        "select a from ApplicationRow a where a.state = :draft and a.filed <= :cutoff"
                                + " order by a.filed, a.reference",
                        ApplicationRow.class)
                .setParameter("draft", ApplicationState.DRAFT)
                .setParameter("cutoff", day.minusDays(olderThan))
                .getResultList();
    }

    private static List<String> lapsing(EntityManager db, LocalDate day, int within) {
        LocalDate horizon = day.plusDays(within);
        List<String> found = new ArrayList<>();
        for (CarrierRow row : db.createQuery("select c from CarrierRow c order by c.name", CarrierRow.class)
                .getResultList()) {
            Optional<InsuranceRow> cover = row.currentInsurance(day);
            if (cover.isPresent()
                    && !day.isAfter(cover.get().getExpires())
                    && !cover.get().getExpires().isAfter(horizon)) {
                found.add(row.getName());
            }
        }
        return found;
    }

    /**
     * Carriers over the line today that were not over it yesterday.
     *
     * <p>Built by reading the book and asking it about two days, which is two passes over a table
     * of a few thousand rows in the worst year this office has had.
     */
    private static List<String> newlyHeld(EntityManager db, LocalDate day) {
        EnforcementBook book = Books.enforcementFrom(db);
        LocalDate yesterday = day.minusDays(1);
        Map<Integer, String> names = new HashMap<>();
        db.createQuery("select c from CarrierRow c", CarrierRow.class)
                .getResultList()
                .forEach(row -> names.put(row.getUsdot(), row.getName()));
        List<String> found = new ArrayList<>();
        for (Account account : book.held(day)) {
            if (book.hold(account.usdot(), yesterday).isPresent()) {
                continue;
            }
            String name = names.getOrDefault(account.usdot(), String.valueOf(account.usdot()));
            found.add(account.usdot() + " " + name + " " + account.owing());
        }
        return found;
    }
}
