package io.github.rkaushikdev.jeepdolly.domain;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Written;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * One carrier, its paper, and its standing.
 *
 * <p>A permit is written to a carrier, not to a load and not to a driver. So before anything is
 * measured, the office asks whether this carrier may have a permit at all, and there are exactly
 * three ways the answer is no: the insurance certificate has lapsed, the surety bond has lapsed for
 * a load that needs one, or the carrier is suspended.
 *
 * <p>A carrier record also holds the billing arrangement, because a permit issued on account and a
 * permit paid at the counter follow different paths later. Suspensions are added over the life of
 * the record, so a carrier is not a value.
 */
public final class Carrier {

    /** Above this weight the state wants a bond behind the permit. */
    public static final int BOND_THRESHOLD_POUNDS = 120000;

    private final int usdot;
    private final String name;
    private final String town;
    private final String state;
    private final Billing billing;
    private final Insurance insurance;
    private final Bond bond;
    private final List<Suspension> suspensions;

    /** A carrier paying at the counter, with no paper on file yet. */
    public Carrier(int usdot, String name, String town, String state) {
        this(builder(usdot, name, town, state));
    }

    private Carrier(Builder built) {
        if (built.usdot <= 0 || built.usdot > 99999999) {
            throw new Impossible(built.usdot + " is not a usdot number");
        }
        if (built.name.isBlank()) {
            throw new Impossible("a carrier has a name");
        }
        if (built.state.length() != 2 || !isUpper(built.state)) {
            throw new Impossible(Written.quoted(built.state) + " is not a state");
        }
        this.usdot = built.usdot;
        this.name = built.name;
        this.town = built.town;
        this.state = built.state;
        this.billing = built.billing;
        this.insurance = built.insurance;
        this.bond = built.bond;
        this.suspensions = new ArrayList<>(built.suspensions);
    }

    private static boolean isUpper(String text) {
        return text.chars().anyMatch(Character::isLetter) && text.equals(text.toUpperCase(Locale.ROOT));
    }

    /** Start a carrier record; billing and paper are set on the builder. */
    public static Builder builder(int usdot, String name, String town, String state) {
        return new Builder(usdot, name, town, state);
    }

    public int usdot() {
        return usdot;
    }

    public String name() {
        return name;
    }

    public String town() {
        return town;
    }

    public String state() {
        return state;
    }

    public Billing billing() {
        return billing;
    }

    public Optional<Insurance> insurance() {
        return Optional.ofNullable(insurance);
    }

    public Optional<Bond> bond() {
        return Optional.ofNullable(bond);
    }

    /** Every suspension ever ordered, in the order they were ordered. Read only. */
    public List<Suspension> suspensions() {
        return Collections.unmodifiableList(suspensions);
    }

    /** Suspend until further notice. */
    public Suspension suspend(String reason, LocalDate fromDay) {
        return record(new Suspension(reason, fromDay));
    }

    /**
     * Suspend until a day.
     *
     * @throws Impossible when the suspension would lift before it starts
     */
    public Suspension suspend(String reason, LocalDate fromDay, LocalDate until) {
        if (until.isBefore(fromDay)) {
            throw new Impossible("a suspension lifts after it starts");
        }
        return record(new Suspension(reason, fromDay, Optional.of(until)));
    }

    private Suspension record(Suspension order) {
        suspensions.add(order);
        return order;
    }

    /** The first suspension in force on a day, if any is. */
    public Optional<Suspension> suspensionOn(LocalDate day) {
        return suspensions.stream().filter(order -> order.inForce(day)).findFirst();
    }

    public boolean needsBond(int grossPounds) {
        return grossPounds > BOND_THRESHOLD_POUNDS;
    }

    /** What stops a permit over this span for a light load, or empty if nothing does. */
    public Optional<String> standing(LocalDate start, LocalDate end) {
        return standing(start, end, 0);
    }

    /**
     * What stops a permit over this span, or empty if nothing does.
     *
     * <p>The order matters. A suspension is reported ahead of lapsed paper, because a carrier that
     * fixes its certificate is still suspended and being told one thing at a time wastes a week.
     */
    public Optional<String> standing(LocalDate start, LocalDate end, int grossPounds) {
        Optional<Suspension> order = suspensionOn(start);
        if (order.isPresent()) {
            return Optional.of(order.get().toString());
        }
        if (insurance == null) {
            return Optional.of("no certificate of insurance on file");
        }
        if (!insurance.covers(start, end)) {
            return Optional.of("insurance " + insurance.policy() + " does not cover the whole trip");
        }
        if (needsBond(grossPounds)) {
            if (bond == null) {
                return Optional.of(String.format(
                        Locale.ROOT, "no bond on file and the load is over %,d lb", BOND_THRESHOLD_POUNDS));
            }
            if (!bond.covers(start, end)) {
                return Optional.of("bond " + bond.number() + " does not cover the whole trip");
            }
        }
        return Optional.empty();
    }

    public boolean inGoodStanding(LocalDate start, LocalDate end) {
        return standing(start, end).isEmpty();
    }

    public boolean inGoodStanding(LocalDate start, LocalDate end, int grossPounds) {
        return standing(start, end, grossPounds).isEmpty();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Carrier that
                && usdot == that.usdot
                && name.equals(that.name)
                && town.equals(that.town)
                && state.equals(that.state)
                && billing == that.billing
                && Objects.equals(insurance, that.insurance)
                && Objects.equals(bond, that.bond)
                && suspensions.equals(that.suspensions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(usdot, name, town, state, billing, insurance, bond, suspensions);
    }

    /** With its number, because names repeat: {@code Bickford Heavy Haul (1204471)}. */
    @Override
    public String toString() {
        return name + " (" + usdot + ")";
    }

    /** The optional parts of a carrier record. */
    public static final class Builder {

        private final int usdot;
        private final String name;
        private final String town;
        private final String state;
        private Billing billing = Billing.COUNTER;
        private Insurance insurance;
        private Bond bond;
        private final List<Suspension> suspensions = new ArrayList<>();

        private Builder(int usdot, String name, String town, String state) {
            this.usdot = usdot;
            this.name = name;
            this.town = town;
            this.state = state;
        }

        public Builder billing(Billing value) {
            this.billing = value;
            return this;
        }

        public Builder insurance(Insurance value) {
            this.insurance = value;
            return this;
        }

        public Builder bond(Bond value) {
            this.bond = value;
            return this;
        }

        public Builder suspensions(List<Suspension> orders) {
            this.suspensions.addAll(orders);
            return this;
        }

        /** @throws Impossible when the usdot number, the name or the state cannot be right */
        public Carrier build() {
            return new Carrier(this);
        }
    }
}
