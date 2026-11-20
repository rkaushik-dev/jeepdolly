package io.github.rkaushikdev.jeepdolly.permits;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Miles;
import io.github.rkaushikdev.jeepdolly.NotAllowed;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.Written;
import io.github.rkaushikdev.jeepdolly.conditions.Condition;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import io.github.rkaushikdev.jeepdolly.fees.Quote;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One issued permit, complete on its own: the document, its life, and what it is evidence of.
 *
 * <p>A permit is the only thing this office produces. Everything else is working out whether to
 * produce it. So the record is complete on its own: a permit that can only be understood by joining
 * it back to the application it came from is a permit nobody can enforce at the roadside at two in
 * the morning.
 *
 * <p>That means the permit copies rather than references. The route is copied as segment idents
 * and as printed narrative. The conditions are copied as rendered text. The fee is copied as lines.
 * If the fee schedule changes tomorrow, or the road log is renumbered, or somebody edits a condition
 * in the library, this permit still says what it said when it was signed.
 *
 * <p>A replacement names the permit it came from and the superseded one names its replacement, so
 * the chain can be walked either way. The state changes over the permit's life, so a permit is not
 * a value; two permits are equal when every field but the history matches, as an application's are.
 */
public final class Permit {

    /** One line of the permit's history: what happened to it, and when. */
    public record Entry(LocalDate on, String note) {}

    private final PermitNumber number;
    private final String application;
    private final int usdot;
    private final String carrierName;
    private final LocalDate issued;
    private final LocalDate start;
    private final LocalDate end;
    private final String origin;
    private final String destination;
    private final List<String> route;
    private final String narrative;
    private final Pounds gross;
    private final Envelope envelope;
    private final Miles distance;
    private final List<Condition> conditions;
    private final Quote quote;
    private PermitState state;
    private String voidReason;
    private PermitNumber supersededBy;
    private final PermitNumber supersedes;
    private final List<Entry> history;

    private Permit(Builder built) {
        this.number = Objects.requireNonNull(built.number, "number");
        this.application = Objects.requireNonNull(built.application, "application");
        this.usdot = built.usdot;
        this.carrierName = Objects.requireNonNull(built.carrierName, "carrier_name");
        this.issued = Objects.requireNonNull(built.issued, "issued");
        this.start = Objects.requireNonNull(built.start, "start");
        this.end = Objects.requireNonNull(built.end, "end");
        this.origin = Objects.requireNonNull(built.origin, "origin");
        this.destination = Objects.requireNonNull(built.destination, "destination");
        this.route = List.copyOf(built.route);
        this.narrative = Objects.requireNonNull(built.narrative, "narrative");
        this.gross = Objects.requireNonNull(built.gross, "gross");
        this.envelope = Objects.requireNonNull(built.envelope, "envelope");
        this.distance = Objects.requireNonNull(built.distance, "distance");
        this.conditions = List.copyOf(built.conditions);
        this.quote = Objects.requireNonNull(built.quote, "quote");
        this.state = Objects.requireNonNull(built.state, "state");
        this.voidReason = Objects.requireNonNull(built.voidReason, "void_reason");
        this.supersededBy = built.supersededBy;
        this.supersedes = built.supersedes;
        this.history = new ArrayList<>(built.history);
        if (end.isBefore(start)) {
            throw new Impossible(number + " ends before it starts");
        }
        if (start.isBefore(issued)) {
            throw new Impossible(number + " is valid before it was issued");
        }
        if (route.isEmpty()) {
            throw new Impossible(number + " names no route");
        }
        if (history.isEmpty()) {
            history.add(new Entry(issued, "issued to " + carrierName));
        }
    }

    /** Start a permit; every field is set on the builder. */
    public static Builder builder() {
        return new Builder();
    }

    /** A builder holding every field of this permit, so a test or a repository can change one. */
    public Builder toBuilder() {
        return builder()
                .number(number)
                .application(application)
                .usdot(usdot)
                .carrierName(carrierName)
                .issued(issued)
                .start(start)
                .end(end)
                .origin(origin)
                .destination(destination)
                .route(route)
                .narrative(narrative)
                .gross(gross)
                .envelope(envelope)
                .distance(distance)
                .conditions(conditions)
                .quote(quote)
                .state(state)
                .voidReason(voidReason)
                .supersededBy(supersededBy)
                .supersedes(supersedes)
                .history(history);
    }

    public PermitNumber number() {
        return number;
    }

    /** The reference of the application the permit was issued against. */
    public String application() {
        return application;
    }

    public int usdot() {
        return usdot;
    }

    public String carrierName() {
        return carrierName;
    }

    public LocalDate issued() {
        return issued;
    }

    /** The first day the permit is good for travel. */
    public LocalDate start() {
        return start;
    }

    /** The last day the permit is good for travel. */
    public LocalDate end() {
        return end;
    }

    public String origin() {
        return origin;
    }

    public String destination() {
        return destination;
    }

    /** The route, copied as segment idents in the order they are driven. */
    public List<String> route() {
        return route;
    }

    /** The route as the driver reads it on the permit face. */
    public String narrative() {
        return narrative;
    }

    public Pounds gross() {
        return gross;
    }

    public Envelope envelope() {
        return envelope;
    }

    public Miles distance() {
        return distance;
    }

    /** The conditions, copied as rendered text, in the order they print. */
    public List<Condition> conditions() {
        return conditions;
    }

    public Quote quote() {
        return quote;
    }

    public PermitState state() {
        return state;
    }

    /** Why it was voided, or empty when it was not. */
    public String voidReason() {
        return voidReason;
    }

    /** The replacement that superseded this permit, if one did. */
    public Optional<PermitNumber> supersededBy() {
        return Optional.ofNullable(supersededBy);
    }

    /** The permit this one replaced, if it is a replacement. */
    public Optional<PermitNumber> supersedes() {
        return Optional.ofNullable(supersedes);
    }

    /** What has happened to the permit, oldest first. Read only. */
    public List<Entry> history() {
        return Collections.unmodifiableList(history);
    }

    /** How many days the permit runs, both ends included. */
    public int days() {
        return (int) ChronoUnit.DAYS.between(start, end) + 1;
    }

    /** Whether the permit's last day has gone by. */
    public boolean expiredOn(LocalDate day) {
        return day.isAfter(end);
    }

    /** Whether this permit is good for travel on a given day. */
    public boolean validOn(LocalDate day) {
        if (state != PermitState.ISSUED) {
            return false;
        }
        return !day.isBefore(start) && !day.isAfter(end);
    }

    /** What a clerk answering the telephone would say. */
    public String statusOn(LocalDate day) {
        if (state == PermitState.VOID) {
            return "void: " + voidReason;
        }
        if (state == PermitState.SUPERSEDED) {
            return "superseded by " + (supersededBy == null ? "None" : supersededBy.toString());
        }
        if (day.isBefore(start)) {
            return "issued, not yet in force";
        }
        if (expiredOn(day)) {
            return "expired";
        }
        return "in force";
    }

    /**
     * Cancel the permit, keeping it on file with the reason.
     *
     * <p>Named for what it does: {@code void} is a Java keyword.
     *
     * @throws NotAllowed when the permit is not live
     * @throws Impossible when no reason is written down
     */
    public void makeVoid(LocalDate on, String reason) {
        if (state != PermitState.ISSUED) {
            throw new NotAllowed(number + " is " + state + " and cannot be voided");
        }
        if (reason.isBlank()) {
            throw new Impossible("a permit is voided for a reason and the reason is written down");
        }
        state = PermitState.VOID;
        voidReason = reason;
        history.add(new Entry(on, "void: " + reason));
    }

    /**
     * Mark the permit replaced by an amendment.
     *
     * @throws NotAllowed when the permit is not live
     */
    public void supersede(LocalDate on, PermitNumber replacement) {
        if (state != PermitState.ISSUED) {
            throw new NotAllowed(number + " is " + state + " and cannot be superseded");
        }
        state = PermitState.SUPERSEDED;
        supersededBy = replacement;
        history.add(new Entry(on, "superseded by " + replacement));
    }

    /** The codes of the conditions, in the order they print. */
    public List<String> conditionCodes() {
        return conditions.stream().map(Condition::code).toList();
    }

    /** The top of the printed permit, which is what gets read first. */
    public String face() {
        return number + "  " + carrierName + " (" + usdot + ")\n"
                + Written.title(origin) + " to " + Written.title(destination) + ", " + distance + "\n"
                + envelope + "\n"
                + gross + " gross, " + start + " to " + end;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Permit that
                && number.equals(that.number)
                && application.equals(that.application)
                && usdot == that.usdot
                && carrierName.equals(that.carrierName)
                && issued.equals(that.issued)
                && start.equals(that.start)
                && end.equals(that.end)
                && origin.equals(that.origin)
                && destination.equals(that.destination)
                && route.equals(that.route)
                && narrative.equals(that.narrative)
                && gross.equals(that.gross)
                && envelope.equals(that.envelope)
                && distance.equals(that.distance)
                && conditions.equals(that.conditions)
                && quote.equals(that.quote)
                && state == that.state
                && voidReason.equals(that.voidReason)
                && Objects.equals(supersededBy, that.supersededBy)
                && Objects.equals(supersedes, that.supersedes);
    }

    /** Hashed on what never changes, since the state does. */
    @Override
    public int hashCode() {
        return Objects.hash(number, application, usdot);
    }

    /** As the register lists it: {@code 25-P-004471 Bickford Heavy Haul issued}. */
    @Override
    public String toString() {
        return number + " " + carrierName + " " + state;
    }

    /** Every field of the permit, set by name. */
    public static final class Builder {

        private PermitNumber number;
        private String application;
        private int usdot;
        private String carrierName;
        private LocalDate issued;
        private LocalDate start;
        private LocalDate end;
        private String origin;
        private String destination;
        private List<String> route = List.of();
        private String narrative;
        private Pounds gross;
        private Envelope envelope;
        private Miles distance;
        private List<Condition> conditions = List.of();
        private Quote quote;
        private PermitState state = PermitState.ISSUED;
        private String voidReason = "";
        private PermitNumber supersededBy;
        private PermitNumber supersedes;
        private List<Entry> history = List.of();

        private Builder() {}

        public Builder number(PermitNumber value) {
            this.number = value;
            return this;
        }

        public Builder application(String value) {
            this.application = value;
            return this;
        }

        public Builder usdot(int value) {
            this.usdot = value;
            return this;
        }

        public Builder carrierName(String value) {
            this.carrierName = value;
            return this;
        }

        public Builder issued(LocalDate value) {
            this.issued = value;
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

        public Builder origin(String value) {
            this.origin = value;
            return this;
        }

        public Builder destination(String value) {
            this.destination = value;
            return this;
        }

        public Builder route(List<String> value) {
            this.route = value;
            return this;
        }

        public Builder narrative(String value) {
            this.narrative = value;
            return this;
        }

        public Builder gross(Pounds value) {
            this.gross = value;
            return this;
        }

        public Builder envelope(Envelope value) {
            this.envelope = value;
            return this;
        }

        public Builder distance(Miles value) {
            this.distance = value;
            return this;
        }

        public Builder conditions(List<Condition> value) {
            this.conditions = value;
            return this;
        }

        public Builder quote(Quote value) {
            this.quote = value;
            return this;
        }

        /** Where the permit stands, for one read back off the file. Issued when never set. */
        public Builder state(PermitState value) {
            this.state = value;
            return this;
        }

        public Builder voidReason(String value) {
            this.voidReason = value;
            return this;
        }

        /** The replacement, or null when there is none. */
        public Builder supersededBy(PermitNumber value) {
            this.supersededBy = value;
            return this;
        }

        /** The permit this one replaces, or null when it replaces none. */
        public Builder supersedes(PermitNumber value) {
            this.supersedes = value;
            return this;
        }

        /** The history read back off the file. An issued line is written when there is none. */
        public Builder history(List<Entry> value) {
            this.history = value;
            return this;
        }

        /**
         * @throws Impossible when the dates run backwards, start before the issue day, or there is
         *     no route
         * @throws NullPointerException when a field was never set
         */
        public Permit build() {
            return new Permit(this);
        }
    }
}
