package io.github.rkaushikdev.jeepdolly.services;

import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.NotAllowed;
import io.github.rkaushikdev.jeepdolly.permits.Application;
import io.github.rkaushikdev.jeepdolly.permits.Assessment;
import io.github.rkaushikdev.jeepdolly.permits.Assessor;
import io.github.rkaushikdev.jeepdolly.permits.Issuer;
import io.github.rkaushikdev.jeepdolly.permits.Office;
import io.github.rkaushikdev.jeepdolly.permits.Permit;
import io.github.rkaushikdev.jeepdolly.permits.Series;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * The counter: the sequence of steps a clerk actually performs, in one place.
 *
 * <p>Get the application, build the office for the right day, assess it, and write down what
 * happened. Every route and command that does any of that calls through here, so none of them can
 * forget the trail line or build the office for the wrong day. What a caller still owns is its own
 * surface: who may ask, what shape comes back. What it does not own is the order of the office
 * procedure.
 *
 * <p>Every method takes the day it is deciding for, and none of them reaches for today, because a
 * permit assessed against today's road when it travels next Tuesday is the bug this office cannot
 * afford twice.
 */
public final class Counter {

    private Counter() {}

    /** What the counter produced: a permit and the assessment behind it. */
    public record Written(Permit permit, Assessment assessment) {

        /** The permit number as it prints. */
        public String number() {
            return permit.number().toString();
        }
    }

    /** The office reference for a new application, off the same counter as a permit number. */
    public static String nextReference(UnitOfWork work, LocalDate on) {
        return String.format(
                Locale.ROOT,
                "%02d-F-%06d",
                Math.floorMod(on.getYear(), 100),
                work.counter().take(on.getYear(), Series.FILE));
    }

    /** Take an application in and record that it was taken in. */
    public static Application fileApplication(UnitOfWork work, Application application, String who, LocalDate on) {
        work.applications().add(application);
        Audit.record(
                work.entityManager(),
                "application.filed",
                who,
                "application",
                application.reference(),
                application.origin() + " to " + application.destination());
        return application;
    }

    /** Move an application into the queue. */
    public static Application submit(UnitOfWork work, String reference, String who, LocalDate on) {
        Application application = work.applications().get(reference);
        application.submit(on);
        work.applications().saveState(application);
        Audit.record(work.entityManager(), "application.submitted", who, "application", reference);
        return application;
    }

    /** The carrier rang up and cancelled. */
    public static Application withdraw(UnitOfWork work, String reference, String who, LocalDate on) {
        Application application = work.applications().get(reference);
        application.withdraw(on);
        work.applications().saveState(application);
        Audit.record(work.entityManager(), "application.withdrawn", who, "application", reference);
        return application;
    }

    /**
     * Name the pilot cars on an open application, or change who they are.
     *
     * <p>The register is read here as well as at assessment, on purpose. A card that is not on the
     * register is nearly always a typing mistake, and the counter wants to hear about it while the
     * carrier is still on the telephone. Whether the cards are good for the day of travel is not
     * asked here: that is the assessment's question, and a card named in March for a move in June
     * may run out in between as long as it is renewed first.
     *
     * @throws NotAllowed when the application is no longer open
     * @throws io.github.rkaushikdev.jeepdolly.NotOnFile when a card is not on the register
     */
    public static Application namePilots(
            UnitOfWork work, Office office, String reference, List<String> cards, String who, LocalDate on) {
        Application application = work.applications().get(reference);
        if (!application.state().open()) {
            throw new NotAllowed(reference + " is " + application.state() + " and the pilots cannot be changed");
        }
        for (String card : cards) {
            office.escorts().operator(card);
        }
        application.nameEscortCards(cards);
        work.applications().saveEscortCards(application);
        Audit.record(
                work.entityManager(),
                "application.pilots_named",
                who,
                "application",
                reference,
                cards.isEmpty() ? "nobody" : String.join(", ", cards));
        return application;
    }

    /** Assess an application for the day it says it is travelling. */
    public static Assessment lookAt(Office office, Application application) {
        return Assessor.assess(office, application, application.start());
    }

    /** Assess an application for a given day. */
    public static Assessment lookAt(Office office, Application application, LocalDate on) {
        return Assessor.assess(office, application, on);
    }

    /** Assess again and issue, which is the only path to a permit number. */
    public static Written writePermit(UnitOfWork work, Office office, String reference, String who, LocalDate on) {
        Application application = work.applications().get(reference);
        Assessment found = lookAt(office, application);
        Permit permit = Issuer.issue(office, found, on);
        work.applications().saveState(application);
        work.permits().add(permit);
        Audit.record(
                work.entityManager(),
                "permit.issued",
                who,
                "permit",
                permit.number().toString(),
                permit.gross() + " to " + permit.carrierName());
        return new Written(permit, found);
    }

    /** Close an application the office will not write. */
    public static Application refuse(UnitOfWork work, String reference, String who, LocalDate on, String reason) {
        Application application = work.applications().get(reference);
        application.refuse(on, reason);
        work.applications().saveState(application);
        Audit.record(work.entityManager(), "application.refused", who, "application", reference, reason);
        return application;
    }

    /** Kill a permit, with the reason on the record. */
    public static Permit voidPermit(UnitOfWork work, String number, String who, LocalDate on, String reason) {
        Permit permit = work.permits().get(number);
        permit.makeVoid(on, reason);
        work.permits().saveState(permit);
        Audit.record(work.entityManager(), "permit.voided", who, "permit", number, reason);
        return permit;
    }

    /**
     * File, submit and issue in one go, which is what a walk in is.
     *
     * <p>Anything that refuses along the way refuses the whole thing, because a hauler standing at
     * the counter with a load on the truck wants a permit or a reason, and not an application
     * reference to ring about tomorrow. The refusal is written down before it is raised.
     *
     * @throws NotAllowed carrying every reason, when the load cannot be permitted
     */
    public static Written overTheCounter(
            UnitOfWork work, Office office, Application application, String who, LocalDate on) {
        fileApplication(work, application, who, on);
        work.flush();
        submit(work, application.reference(), who, on);
        Assessment found = lookAt(office, application);
        if (!found.permittable()) {
            refuse(work, application.reference(), who, on, found.refusals().get(0));
            throw new NotAllowed(
                    application.reference() + " cannot be issued",
                    Facts.of("reasons", found.refusals(), "reference", application.reference()));
        }
        return writePermit(work, office, application.reference(), who, on);
    }
}
