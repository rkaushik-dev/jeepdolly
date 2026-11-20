package io.github.rkaushikdev.jeepdolly.permits;

import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.NotAllowed;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.domain.Combination;
import io.github.rkaushikdev.jeepdolly.domain.Piece;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One request for one permit, as it was submitted, before anybody checks it.
 *
 * <p>The application is deliberately a dumb record. It holds what was submitted, exactly as
 * submitted, and it does not correct anything. If a hauler declares a combination that cannot
 * exist, the application still holds it, because the refusal letter has to quote what was asked
 * for and not a tidied version of it.
 *
 * <p>What the application does know is whether it is complete enough to be looked at. That is a
 * different question from whether it can be permitted, and keeping the two apart is the reason the
 * counter can hand something back in thirty seconds instead of after a route check.
 *
 * <p>The travel span is on the application because the hauler asks for dates and the office grants
 * them, and a permit that quietly starts on a different day from the one asked for is a permit that
 * gets used on the wrong day. The pilot car cards are here for the same reason and they are allowed
 * to be empty: a carrier does not know on Monday who is free on Thursday.
 *
 * <p>Two applications are the same application when they ask for the same thing, whatever has
 * happened to each of them since, so the history is not part of equality.
 */
public final class Application {

    /** A single trip permit may not be asked for over a longer span than this. */
    public static final int MAX_SPAN_DAYS = 10;

    /**
     * One line of the file's history.
     *
     * @param state where the application stood after this happened
     */
    public record Entry(LocalDate on, ApplicationState state, String note) {}

    private final String reference;
    private final int usdot;
    private final String commodity;
    private final List<Piece> pieces;
    private final Combination combination;
    private final String origin;
    private final String destination;
    private final LocalDate start;
    private final LocalDate end;
    private final Applicant applicant;
    private final LocalDate filed;
    private final List<String> route;
    private List<String> escortCards;
    private ApplicationState state;
    private final String notes;
    private final List<Entry> history;

    private Application(Builder built) {
        this.reference = Objects.requireNonNull(built.reference, "reference");
        this.usdot = built.usdot;
        this.commodity = Objects.requireNonNull(built.commodity, "commodity");
        this.pieces = List.copyOf(built.pieces);
        this.combination = Objects.requireNonNull(built.combination, "combination");
        this.origin = Objects.requireNonNull(built.origin, "origin");
        this.destination = Objects.requireNonNull(built.destination, "destination");
        this.start = Objects.requireNonNull(built.start, "start");
        this.end = Objects.requireNonNull(built.end, "end");
        this.applicant = Objects.requireNonNull(built.applicant, "applicant");
        this.filed = Objects.requireNonNull(built.filed, "filed");
        this.route = List.copyOf(built.route);
        this.escortCards = List.copyOf(built.escortCards);
        this.state = Objects.requireNonNull(built.state, "state");
        this.notes = Objects.requireNonNull(built.notes, "notes");
        this.history = new ArrayList<>(built.history);
        if (reference.isBlank()) {
            throw new Impossible("an application carries a reference");
        }
        if (history.isEmpty()) {
            history.add(new Entry(filed, state, "typed"));
        }
    }

    /** Start an application; every field of the form is set on the builder. */
    public static Builder builder() {
        return new Builder();
    }

    public String reference() {
        return reference;
    }

    public int usdot() {
        return usdot;
    }

    /** The commodity code the hauler declared. */
    public String commodity() {
        return commodity;
    }

    public List<Piece> pieces() {
        return pieces;
    }

    public Combination combination() {
        return combination;
    }

    public String origin() {
        return origin;
    }

    public String destination() {
        return destination;
    }

    /** The first day of travel asked for. */
    public LocalDate start() {
        return start;
    }

    /** The last day of travel asked for. */
    public LocalDate end() {
        return end;
    }

    public Applicant applicant() {
        return applicant;
    }

    public LocalDate filed() {
        return filed;
    }

    /** The segments the hauler wrote, or empty when the office is to find the route. */
    public List<String> route() {
        return route;
    }

    /** The pilot car cards named so far, often none when the application is first typed. */
    public List<String> escortCards() {
        return escortCards;
    }

    public ApplicationState state() {
        return state;
    }

    public String notes() {
        return notes;
    }

    /** Everything that has happened to the file, oldest first. Read only. */
    public List<Entry> history() {
        return Collections.unmodifiableList(history);
    }

    /** Days of travel asked for, both ends included. */
    public int spanDays() {
        return (int) ChronoUnit.DAYS.between(start, end) + 1;
    }

    /** Everything on the road, off the combination. */
    public Pounds gross() {
        return combination.gross();
    }

    /**
     * Name the pilot cars, or change who they are. Usually the day before the move, and checked
     * against the register by whoever names them.
     */
    public void nameEscortCards(List<String> cards) {
        this.escortCards = List.copyOf(cards);
    }

    /** What stops this being looked at, which is not why it might fail. */
    public List<String> problems() {
        List<String> found = new ArrayList<>();
        if (end.isBefore(start)) {
            found.add("the travel span ends before it starts");
        } else if (spanDays() > MAX_SPAN_DAYS) {
            found.add("a single trip permit runs " + MAX_SPAN_DAYS + " days at most");
        }
        if (start.isBefore(filed)) {
            found.add("the travel span starts before the application was filed");
        }
        if (pieces.isEmpty()) {
            found.add("nothing is described on the trailer");
        }
        if (origin.equals(destination)) {
            found.add("the trip starts and ends in the same place");
        }
        if (origin.isBlank() || destination.isBlank()) {
            found.add("the trip has an origin and a destination");
        }
        return List.copyOf(found);
    }

    /** Whether there is nothing to say about it before it can be looked at. */
    public boolean complete() {
        return problems().isEmpty();
    }

    private void move(ApplicationState next, LocalDate on, String note) {
        state = next;
        history.add(new Entry(on, next, note));
    }

    /**
     * Put a draft in the queue.
     *
     * @throws NotAllowed when it is not a draft
     * @throws Impossible when it is not complete, naming the first reason
     */
    public void submit(LocalDate on) {
        if (state != ApplicationState.DRAFT) {
            throw new NotAllowed(reference + " is " + state + " and cannot be submitted");
        }
        List<String> problems = problems();
        if (!problems.isEmpty()) {
            throw new Impossible(
                    reference + " is not complete: " + problems.get(0),
                    Facts.of("reference", reference, "problems", problems));
        }
        move(ApplicationState.SUBMITTED, on, "submitted");
    }

    /** Send a submitted application to an engineer. */
    public void sendForReview(LocalDate on) {
        sendForReview(on, "");
    }

    /**
     * Send a submitted application to an engineer, saying why.
     *
     * @throws NotAllowed when it is not submitted
     */
    public void sendForReview(LocalDate on, String note) {
        if (state != ApplicationState.SUBMITTED) {
            throw new NotAllowed(reference + " is " + state + " and cannot go to review");
        }
        move(ApplicationState.IN_REVIEW, on, note.isEmpty() ? "sent to an engineer" : note);
    }

    /**
     * Close the file as issued under a permit number.
     *
     * @throws NotAllowed when it is neither submitted nor in review
     */
    public void issue(LocalDate on, String number) {
        if (state != ApplicationState.SUBMITTED && state != ApplicationState.IN_REVIEW) {
            throw new NotAllowed(reference + " is " + state + " and cannot be issued");
        }
        move(ApplicationState.ISSUED, on, "issued as " + number);
    }

    /**
     * Close the file as refused, with the reason the letter gives.
     *
     * @throws NotAllowed when the file is already closed
     */
    public void refuse(LocalDate on, String reason) {
        if (!state.open()) {
            throw new NotAllowed(reference + " is " + state + " and cannot be refused");
        }
        move(ApplicationState.REFUSED, on, reason);
    }

    /** Close the file because the carrier withdrew it. */
    public void withdraw(LocalDate on) {
        withdraw(on, "withdrawn by the carrier");
    }

    /**
     * Close the file because it was withdrawn, saying why.
     *
     * @throws NotAllowed when the file is already closed
     */
    public void withdraw(LocalDate on, String reason) {
        if (!state.open()) {
            throw new NotAllowed(reference + " is " + state + " and cannot be withdrawn");
        }
        move(ApplicationState.WITHDRAWN, on, reason);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Application that
                && reference.equals(that.reference)
                && usdot == that.usdot
                && commodity.equals(that.commodity)
                && pieces.equals(that.pieces)
                && combination.equals(that.combination)
                && origin.equals(that.origin)
                && destination.equals(that.destination)
                && start.equals(that.start)
                && end.equals(that.end)
                && applicant.equals(that.applicant)
                && filed.equals(that.filed)
                && route.equals(that.route)
                && escortCards.equals(that.escortCards)
                && state == that.state
                && notes.equals(that.notes);
    }

    /** Hashed on what never changes, since the state and the cards do. */
    @Override
    public int hashCode() {
        return Objects.hash(reference, usdot, filed);
    }

    /** As the queue lists it: {@code 25-04471 waterville to pittsfield draft}. */
    @Override
    public String toString() {
        return reference + " " + origin + " to " + destination + " " + state;
    }

    /** Every field of the application form, set by name. */
    public static final class Builder {

        private String reference;
        private int usdot;
        private String commodity;
        private List<Piece> pieces = List.of();
        private Combination combination;
        private String origin;
        private String destination;
        private LocalDate start;
        private LocalDate end;
        private Applicant applicant;
        private LocalDate filed;
        private List<String> route = List.of();
        private List<String> escortCards = List.of();
        private ApplicationState state = ApplicationState.DRAFT;
        private String notes = "";
        private List<Entry> history = List.of();

        private Builder() {}

        public Builder reference(String value) {
            this.reference = value;
            return this;
        }

        public Builder usdot(int value) {
            this.usdot = value;
            return this;
        }

        public Builder commodity(String value) {
            this.commodity = value;
            return this;
        }

        public Builder pieces(List<Piece> value) {
            this.pieces = value;
            return this;
        }

        public Builder combination(Combination value) {
            this.combination = value;
            return this;
        }

        public Builder origin(String value) {
            this.origin = value;
            return this;
        }

        public Builder destination(String value) {
            this.destination = value;
            return this;
        }

        public Builder start(LocalDate value) {
            this.start = value;
            return this;
        }

        public Builder end(LocalDate value) {
            this.end = value;
            return this;
        }

        public Builder applicant(Applicant value) {
            this.applicant = value;
            return this;
        }

        public Builder filed(LocalDate value) {
            this.filed = value;
            return this;
        }

        public Builder route(List<String> value) {
            this.route = value;
            return this;
        }

        public Builder escortCards(List<String> value) {
            this.escortCards = value;
            return this;
        }

        /** Where it stands, for an application read back off the file. Draft when never set. */
        public Builder state(ApplicationState value) {
            this.state = value;
            return this;
        }

        public Builder notes(String value) {
            this.notes = value;
            return this;
        }

        /** The history read back off the file. A typed line is written when there is none. */
        public Builder history(List<Entry> value) {
            this.history = value;
            return this;
        }

        /**
         * @throws Impossible when there is no reference
         * @throws NullPointerException when a field of the form was never set
         */
        public Application build() {
            return new Application(this);
        }
    }
}
