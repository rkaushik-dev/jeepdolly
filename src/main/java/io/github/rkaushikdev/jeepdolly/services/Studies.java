package io.github.rkaushikdev.jeepdolly.services;

import io.github.rkaushikdev.jeepdolly.Conflict;
import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import io.github.rkaushikdev.jeepdolly.persistence.models.ReviewMoveRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.ReviewRow;
import io.github.rkaushikdev.jeepdolly.structures.Review;
import io.github.rkaushikdev.jeepdolly.structures.ReviewState;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

/**
 * The engineering study workflow, kept on the database rather than in memory.
 *
 * <p>The queue in {@link Review} is the rules: five states, four moves, and a history nobody edits.
 * This is the same thing with rows behind it, and it exists because the queue is the one part of
 * the office that spans days. A review opened on Monday is picked up on Wednesday by somebody who
 * was not there on Monday, and none of that survives in a process.
 *
 * <p>The review is rebuilt from its rows for every move and written back afterwards. That is slower
 * than editing the row directly and it is the reason the state machine cannot be got round: every
 * move goes through the same {@code assign} and {@code clear} the domain rules hold to. Moves are
 * appended, never rewritten, and their position is the count of what is already there, so two
 * moves on one day keep the order they happened in.
 */
public final class Studies {

    private static final List<ReviewState> OPEN =
            List.of(ReviewState.WAITING, ReviewState.WITH_ENGINEER, ReviewState.HELD);

    private Studies() {}

    /**
     * Put an application in the queue.
     *
     * @throws Conflict when the application is already in it
     */
    public static ReviewRow openStudy(EntityManager db, String reference, List<String> structures, LocalDate on) {
        if (db.find(ReviewRow.class, reference) != null) {
            throw new Conflict(reference + " is already in the queue", Facts.of("reference", reference));
        }
        ReviewRow row = new ReviewRow(reference, on, ReviewState.WAITING);
        row.setStructures(String.join(",", structures));
        row.addMove(new ReviewMoveRow(0, on, "counter", ReviewState.WAITING, "opened"));
        db.persist(row);
        return row;
    }

    /** @throws NotOnFile when nobody opened a study for the reference */
    public static ReviewRow rowFor(EntityManager db, String reference) {
        ReviewRow found = db.find(ReviewRow.class, reference);
        if (found == null) {
            throw new NotOnFile("no review for " + reference, Facts.of("reference", reference));
        }
        return found;
    }

    /** The rows as the state machine sees them, with no history of its own. */
    public static Review toDomain(ReviewRow row) {
        return Review.restore(
                row.getReference(),
                split(row.getStructures(), ","),
                row.getOpened(),
                row.getState(),
                row.getEngineer(),
                split(row.getConditions(), "\\|"),
                List.of());
    }

    private static List<String> split(String written, String separator) {
        return Arrays.stream(written.split(separator, -1))
                .filter(one -> !one.isEmpty())
                .toList();
    }

    private static ReviewMoveRow writeBack(ReviewRow row, Review review, LocalDate on, String who, String note) {
        ReviewMoveRow move = row.addMove(new ReviewMoveRow(row.getMoves().size(), on, who, review.state(), note));
        row.setState(review.state());
        row.setEngineer(review.engineer().orElse(null));
        row.setConditions(String.join("|", review.conditions()));
        return move;
    }

    /** An engineer picks a study up. */
    public static ReviewRow assign(EntityManager db, String reference, String engineer, LocalDate on) {
        ReviewRow row = rowFor(db, reference);
        Review review = toDomain(row);
        review.assign(engineer, on);
        writeBack(row, review, on, engineer, "picked up");
        return row;
    }

    /** The engineer wants something before going further. */
    public static ReviewRow hold(EntityManager db, String reference, LocalDate on, String reason) {
        ReviewRow row = rowFor(db, reference);
        Review review = toDomain(row);
        review.hold(on, reason);
        writeBack(row, review, on, row.getEngineer().orElse("engineer"), reason);
        return row;
    }

    /** What was asked for arrived, and the clock starts again. */
    public static ReviewRow resume(EntityManager db, String reference, LocalDate on) {
        return resume(db, reference, on, "answered");
    }

    /** What was asked for arrived, and the clock starts again. */
    public static ReviewRow resume(EntityManager db, String reference, LocalDate on, String note) {
        ReviewRow row = rowFor(db, reference);
        Review review = toDomain(row);
        review.resume(on, note);
        writeBack(row, review, on, "counter", note);
        return row;
    }

    /** The engineer signed it with no conditions. */
    public static ReviewRow clear(EntityManager db, String reference, LocalDate on) {
        return clear(db, reference, on, List.of());
    }

    /** The engineer signed it, with the conditions written on the clearance. */
    public static ReviewRow clear(EntityManager db, String reference, LocalDate on, List<String> conditions) {
        ReviewRow row = rowFor(db, reference);
        Review review = toDomain(row);
        String signer = row.getEngineer().orElse("engineer");
        review.clear(on, conditions);
        writeBack(row, review, on, signer, "signed");
        return row;
    }

    /** The engineer signed it the other way. */
    public static ReviewRow refuse(EntityManager db, String reference, LocalDate on, String reason) {
        ReviewRow row = rowFor(db, reference);
        Review review = toDomain(row);
        String signer = row.getEngineer().orElse("engineer");
        review.refuse(on, reason);
        writeBack(row, review, on, signer, reason);
        return row;
    }

    /** The queue, oldest first, which is the order it is worked. */
    public static List<ReviewRow> waiting(EntityManager db) {
        return db.createQuery(
                        "select r from ReviewRow r where r.state = :state order by r.opened, r.reference",
                        ReviewRow.class)
                .setParameter("state", ReviewState.WAITING)
                .getResultList();
    }

    /** What one engineer has on the desk, by reference. */
    public static List<ReviewRow> withEngineer(EntityManager db, String engineer) {
        return db.createQuery(
                        "select r from ReviewRow r where r.engineer = :engineer and r.state = :state"
                                + " order by r.reference",
                        ReviewRow.class)
                .setParameter("engineer", engineer)
                .setParameter("state", ReviewState.WITH_ENGINEER)
                .getResultList();
    }

    /** Open studies that have been waiting longer than the office promises, oldest first. */
    public static List<ReviewRow> overdue(EntityManager db, LocalDate on, int days) {
        return db
                .createQuery("select r from ReviewRow r where r.state in :open order by r.opened", ReviewRow.class)
                .setParameter("open", OPEN)
                .getResultList()
                .stream()
                .filter(one -> ChronoUnit.DAYS.between(one.getOpened(), on) > days)
                .toList();
    }
}
