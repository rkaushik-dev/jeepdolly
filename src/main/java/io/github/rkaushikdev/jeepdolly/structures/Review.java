package io.github.rkaushikdev.jeepdolly.structures;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.NotAllowed;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * One engineering study, its state, and everything that has happened to it.
 *
 * <p>Anything the counter cannot issue lands in the review queue. It is the part of the office
 * people telephone about, so the state of an item has to be exactly right and its history complete.
 * Five states and four moves between them: an engineer picks it up, then clears it with conditions,
 * refuses it with a reason, or holds it for something from the hauler, after which it goes back to
 * waiting and the clock starts again.
 *
 * <p>Every move is recorded with who and when. Nothing is overwritten and nothing is deleted,
 * because the one item anybody ever asks about in a lawsuit is the one that was edited. The
 * conditions of a cleared item go onto the permit unchanged: the office does not paraphrase an
 * engineer.
 */
public final class Review {

    private final String reference;
    private final List<String> structures;
    private final LocalDate opened;
    private ReviewState state;
    private String engineer;
    private List<String> conditions;
    private final List<Move> history;

    /** A new item, waiting, with the line that opened it as its history. */
    public Review(String reference, List<String> structures, LocalDate opened) {
        this(reference, structures, opened, ReviewState.WAITING, Optional.empty(), List.of(), List.of());
        history.add(new Move(opened, "counter", ReviewState.WAITING, "opened"));
    }

    private Review(
            String reference,
            List<String> structures,
            LocalDate opened,
            ReviewState state,
            Optional<String> engineer,
            List<String> conditions,
            List<Move> history) {
        if (reference.isBlank()) {
            throw new Impossible("a review is opened against an application");
        }
        this.reference = reference;
        this.structures = List.copyOf(structures);
        this.opened = opened;
        this.state = state;
        this.engineer = engineer.orElse(null);
        this.conditions = List.copyOf(conditions);
        this.history = new ArrayList<>(history);
    }

    /**
     * An item as it stands on file, with exactly the history given, which may be empty.
     *
     * <p>The service that keeps the queue in the database reads the moves from their own rows, so
     * the item it works on carries no opening line of its own.
     */
    public static Review restore(
            String reference,
            List<String> structures,
            LocalDate opened,
            ReviewState state,
            Optional<String> engineer,
            List<String> conditions,
            List<Move> history) {
        return new Review(reference, structures, opened, state, engineer, conditions, history);
    }

    public String reference() {
        return reference;
    }

    /** The structures the study is about. */
    public List<String> structures() {
        return structures;
    }

    public LocalDate opened() {
        return opened;
    }

    public ReviewState state() {
        return state;
    }

    /** Who has it, once somebody has picked it up and until it goes back to waiting. */
    public Optional<String> engineer() {
        return Optional.ofNullable(engineer);
    }

    /** What the engineer wrote on a clearance, unchanged. */
    public List<String> conditions() {
        return conditions;
    }

    /** Every move, in the order it happened. Read only. */
    public List<Move> history() {
        return Collections.unmodifiableList(history);
    }

    private Move move(ReviewState next, String by, LocalDate on, String note) {
        if (on.isBefore(opened)) {
            throw new Impossible("a review cannot move before it was opened");
        }
        Move made = new Move(on, by, next, note);
        state = next;
        history.add(made);
        return made;
    }

    private String signer() {
        return engineer == null || engineer.isEmpty() ? "engineer" : engineer;
    }

    /** @throws NotAllowed unless the item is waiting */
    public Move assign(String who, LocalDate on) {
        if (state != ReviewState.WAITING) {
            throw new NotAllowed(reference + " is " + state + " and cannot be picked up");
        }
        engineer = who;
        return move(ReviewState.WITH_ENGINEER, who, on, "picked up");
    }

    /** @throws NotAllowed unless an engineer has the item */
    public Move hold(LocalDate on, String reason) {
        if (state != ReviewState.WITH_ENGINEER) {
            throw new NotAllowed(reference + " is " + state + " and cannot be held");
        }
        return move(ReviewState.HELD, signer(), on, reason);
    }

    /** What was asked for arrived. */
    public Move resume(LocalDate on) {
        return resume(on, "answered");
    }

    /** @throws NotAllowed unless the item is held */
    public Move resume(LocalDate on, String note) {
        if (state != ReviewState.HELD) {
            throw new NotAllowed(reference + " is " + state + " and is not on hold");
        }
        engineer = null;
        return move(ReviewState.WAITING, "counter", on, note);
    }

    /** Signed with nothing attached. */
    public Move clear(LocalDate on) {
        return clear(on, List.of());
    }

    /** @throws NotAllowed unless an engineer has the item */
    public Move clear(LocalDate on, List<String> written) {
        if (state != ReviewState.WITH_ENGINEER) {
            throw new NotAllowed(reference + " is " + state + " and cannot be cleared");
        }
        conditions = List.copyOf(written);
        return move(ReviewState.CLEARED, signer(), on, "signed");
    }

    /** @throws NotAllowed unless an engineer has the item */
    public Move refuse(LocalDate on, String reason) {
        if (state != ReviewState.WITH_ENGINEER) {
            throw new NotAllowed(reference + " is " + state + " and cannot be refused");
        }
        return move(ReviewState.REFUSED, signer(), on, reason);
    }

    /** Days since it was opened, which is the number the queue is sorted on. */
    public int waitingDays(LocalDate on) {
        return (int) ChronoUnit.DAYS.between(opened, on);
    }

    /** The day of the latest decision, if it has been decided. */
    public Optional<LocalDate> decidedOn() {
        for (int index = history.size() - 1; index >= 0; index--) {
            Move one = history.get(index);
            if (one.state().decided()) {
                return Optional.of(one.on());
            }
        }
        return Optional.empty();
    }

    /** As the counter reads it out: {@code 25-04471 with engineer with R Pelletier}. */
    @Override
    public String toString() {
        String withWhom = engineer == null || engineer.isEmpty() ? "" : " with " + engineer;
        return reference + " " + state + withWhom;
    }
}
