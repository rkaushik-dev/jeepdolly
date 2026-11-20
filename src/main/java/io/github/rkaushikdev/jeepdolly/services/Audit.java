package io.github.rkaushikdev.jeepdolly.services;

import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Written;
import io.github.rkaushikdev.jeepdolly.persistence.models.AuditRow;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Writing to the audit trail, and the vocabulary of actions that may be written.
 *
 * <p>Every action is one of the words in {@link #ACTIONS} and a caller that invents one is refused.
 * That looks fussy and it is the whole value of the table: a trail where half the rows say {@code
 * permit.issued} and half say {@code issued permit} is a trail nobody can count.
 *
 * <p>The trail is written inside the caller's transaction. If the work rolls back, so does the
 * record of it, which is right: the trail says what happened, and something that was rolled back
 * did not happen. Reading is by document or by person, both of which are the questions actually
 * asked.
 */
public final class Audit {

    /** Every action the trail knows about. Adding one is a deliberate act. */
    public static final Set<String> ACTIONS = Set.of(
            "application.filed",
            "application.submitted",
            "application.withdrawn",
            "application.refused",
            "application.pilots_named",
            "permit.issued",
            "permit.voided",
            "permit.reprinted",
            "permit.renewed",
            "permit.amended",
            "review.opened",
            "review.assigned",
            "review.cleared",
            "review.refused",
            "carrier.registered",
            "carrier.suspended",
            "carrier.restored",
            "road.closed",
            "road.opened",
            "structure.posted",
            "tariff.filed",
            "pilot.company_filed",
            "pilot.company_suspended",
            "pilot.company_restored",
            "pilot.card_issued",
            "pilot.card_suspended",
            "pilot.card_restored",
            "citation.filed",
            "citation.contested",
            "citation.upheld",
            "citation.dismissed",
            "citation.paid",
            "citation.voided",
            "payment.taken",
            "key.issued",
            "key.revoked");

    private Audit() {}

    /** Write one line on the trail, stamped now, with no detail. */
    public static AuditRow record(EntityManager db, String action, String actor, String kind, String reference) {
        return record(db, action, actor, kind, reference, LocalDateTime.now(), "");
    }

    /** Write one line on the trail, stamped now. */
    public static AuditRow record(
            EntityManager db, String action, String actor, String kind, String reference, String detail) {
        return record(db, action, actor, kind, reference, LocalDateTime.now(), detail);
    }

    /** Write one line on the trail at a given moment, with no detail. */
    public static AuditRow record(
            EntityManager db, String action, String actor, String kind, String reference, LocalDateTime at) {
        return record(db, action, actor, kind, reference, at, "");
    }

    /**
     * Write one line on the trail, in the caller's transaction.
     *
     * @throws Impossible when the action is not in the vocabulary or nobody is named as doing it
     */
    public static AuditRow record(
            EntityManager db,
            String action,
            String actor,
            String kind,
            String reference,
            LocalDateTime at,
            String detail) {
        if (!ACTIONS.contains(action)) {
            throw new Impossible(
                    Written.quoted(action) + " is not an action the trail knows", Facts.of("action", action));
        }
        if (actor.isBlank()) {
            throw new Impossible("something did this and the trail says who");
        }
        AuditRow row = new AuditRow(at, action, actor, kind, reference, detail);
        db.persist(row);
        return row;
    }

    /** Everything that has happened to one document, oldest first. */
    public static List<AuditRow> forDocument(EntityManager db, String kind, String reference) {
        return db.createQuery(
                        "select a from AuditRow a where a.kind = :kind and a.reference = :reference"
                                + " order by a.happened, a.id",
                        AuditRow.class)
                .setParameter("kind", kind)
                .setParameter("reference", reference)
                .getResultList();
    }

    /** Everything one person has done, newest first. */
    public static List<AuditRow> byPerson(EntityManager db, String actor) {
        return db.createQuery(
                        "select a from AuditRow a where a.actor = :actor order by a.happened desc, a.id desc",
                        AuditRow.class)
                .setParameter("actor", actor)
                .getResultList();
    }

    /** Every time one thing was done, for the monthly count. */
    public static List<AuditRow> ofAction(EntityManager db, String action) {
        return db.createQuery("select a from AuditRow a where a.action = :action order by a.happened", AuditRow.class)
                .setParameter("action", action)
                .getResultList();
    }

    /**
     * The trail for a span, both ends included, which is what an auditor asks for.
     *
     * @throws Impossible when the span ends before it starts
     */
    public static List<AuditRow> between(EntityManager db, LocalDateTime start, LocalDateTime end) {
        if (end.isBefore(start)) {
            throw new Impossible("a span of the trail ends after it starts");
        }
        return db.createQuery(
                        "select a from AuditRow a where a.happened >= :start and a.happened <= :end"
                                + " order by a.happened, a.id",
                        AuditRow.class)
                .setParameter("start", start)
                .setParameter("end", end)
                .getResultList();
    }
}
