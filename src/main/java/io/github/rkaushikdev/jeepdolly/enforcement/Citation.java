package io.github.rkaushikdev.jeepdolly.enforcement;

import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.NotAllowed;
import io.github.rkaushikdev.jeepdolly.Pounds;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A citation: what an officer wrote at the roadside, and what became of it.
 *
 * <p>The office does not write these. An inspector at a scale or a trooper on US 2 writes one on a
 * pad, and a copy reaches the office three days later, which is why the day it was written and the
 * day it was filed are two different fields and neither is today.
 *
 * <p>A citation names the carrier and it may name a permit. A load stopped with no permit at all
 * still names a carrier, because the usdot number is on the door, and a load stopped over the
 * weight on a good permit names the permit as well, because the argument that follows is about
 * what the permit allowed.
 *
 * <p>There is no route from paid back to anything. A carrier who pays and then finds the citation
 * was wrong gets a refund through the till, which is a different record and a different signature,
 * and a system that let a paid citation be reopened would let the money and the paper disagree.
 *
 * <p>The history is not part of equality: two citations are the same citation when they say the
 * same thing.
 */
public final class Citation {

    /** How long a carrier has to contest one, counted from the day it was written. */
    public static final int CONTEST_DAYS = 21;

    /** One move in a citation's life: the day, the state it moved to, and why. */
    public record Entry(LocalDate on, CitationState state, String note) {}

    private final String number;
    private final LocalDate written;
    private final LocalDate filed;
    private final String officer;
    private final int usdot;
    private final String carrierName;
    private final Offence offence;
    private final String place;
    private final Assessed assessed;
    private final String permit;
    private final Pounds over;
    private final String note;
    private CitationState state;
    private final List<Entry> history = new ArrayList<>();

    private Citation(Builder built) {
        this.number = Objects.requireNonNull(built.number, "number");
        this.written = Objects.requireNonNull(built.written, "written");
        this.filed = Objects.requireNonNull(built.filed, "filed");
        this.officer = Objects.requireNonNull(built.officer, "officer");
        this.usdot = built.usdot;
        this.carrierName = Objects.requireNonNull(built.carrierName, "carrierName");
        this.offence = Objects.requireNonNull(built.offence, "offence");
        this.place = Objects.requireNonNull(built.place, "place");
        this.assessed = Objects.requireNonNull(built.assessed, "assessed");
        this.permit = built.permit;
        this.over = built.over;
        this.note = built.note;
        this.state = built.state;
        if (number.isBlank()) {
            throw new Impossible("a citation carries a number");
        }
        if (filed.isBefore(written)) {
            throw new Impossible(number + " was filed before it was written");
        }
        if (offence.byWeight() && over == null) {
            throw new Impossible(number + " is overweight and does not say by how much");
        }
        history.add(new Entry(filed, state, "filed"));
    }

    /** Start a citation; every field the officer's pad carries is set on the builder. */
    public static Builder builder() {
        return new Builder();
    }

    public String number() {
        return number;
    }

    public LocalDate written() {
        return written;
    }

    public LocalDate filed() {
        return filed;
    }

    public String officer() {
        return officer;
    }

    public int usdot() {
        return usdot;
    }

    public String carrierName() {
        return carrierName;
    }

    public Offence offence() {
        return offence;
    }

    public String place() {
        return place;
    }

    public Assessed assessed() {
        return assessed;
    }

    /** The permit the load was stopped under, if it had one. */
    public Optional<String> permit() {
        return Optional.ofNullable(permit);
    }

    /** By how much it was over, for an overweight citation. */
    public Optional<Pounds> over() {
        return Optional.ofNullable(over);
    }

    public String note() {
        return note;
    }

    public CitationState state() {
        return state;
    }

    /** Every move the citation has made, the filing first. Read only. */
    public List<Entry> history() {
        return Collections.unmodifiableList(history);
    }

    /** What is due on it, which is what the assessment came to. */
    public Dollars amount() {
        return assessed.total();
    }

    /** The last day a carrier may contest it. */
    public LocalDate contestCloses() {
        return written.plusDays(CONTEST_DAYS);
    }

    public boolean contestableOn(LocalDate day) {
        return state == CitationState.WRITTEN && !day.isAfter(contestCloses());
    }

    private void move(CitationState to, LocalDate on, String why) {
        state = to;
        history.add(new Entry(on, to, why));
    }

    /**
     * The carrier says no, in writing, inside the window.
     *
     * @throws NotAllowed when it is not written, or the window has closed
     * @throws Impossible when no grounds are given
     */
    public void contest(LocalDate on, String reason) {
        if (state != CitationState.WRITTEN) {
            throw new NotAllowed(number + " is " + state + " and cannot be contested");
        }
        if (on.isAfter(contestCloses())) {
            throw new NotAllowed(number + " could be contested until " + contestCloses(), Facts.of("number", number));
        }
        if (reason.isBlank()) {
            throw new Impossible("a contested citation says on what grounds");
        }
        move(CitationState.CONTESTED, on, reason);
    }

    /** The hearing went the office's way and it still wants paying. */
    public void uphold(LocalDate on) {
        uphold(on, "");
    }

    /**
     * The hearing went the office's way and it still wants paying.
     *
     * @throws NotAllowed when it was never contested
     */
    public void uphold(LocalDate on, String why) {
        if (state != CitationState.CONTESTED) {
            throw new NotAllowed(number + " is " + state + " and there is nothing to uphold");
        }
        move(CitationState.UPHELD, on, why.isEmpty() ? "upheld at hearing" : why);
    }

    /**
     * The hearing went the carrier's way.
     *
     * @throws NotAllowed when it was never contested
     * @throws Impossible when no reason is given
     */
    public void dismiss(LocalDate on, String reason) {
        if (state != CitationState.CONTESTED) {
            throw new NotAllowed(number + " is " + state + " and there is nothing to dismiss");
        }
        if (reason.isBlank()) {
            throw new Impossible("a dismissed citation says why");
        }
        move(CitationState.DISMISSED, on, reason);
    }

    /** Money in, which is the end of it whichever way it got here. */
    public void pay(LocalDate on) {
        pay(on, "");
    }

    /**
     * Money in, which is the end of it whichever way it got here.
     *
     * @throws NotAllowed when it is not owing
     */
    public void pay(LocalDate on, String reference) {
        if (state != CitationState.WRITTEN && state != CitationState.UPHELD) {
            throw new NotAllowed(number + " is " + state + " and is not owing");
        }
        move(CitationState.PAID, on, reference.isEmpty() ? "paid" : reference);
    }

    /**
     * The office's own mistake, killed by a supervisor.
     *
     * @throws NotAllowed when it is paid, which is refunded instead, or already void
     * @throws Impossible when no reason is given
     */
    public void makeVoid(LocalDate on, String reason) {
        if (state == CitationState.PAID) {
            throw new NotAllowed(number + " is paid and cannot be voided, refund it instead");
        }
        if (state == CitationState.VOID) {
            throw new NotAllowed(number + " is already void");
        }
        if (reason.isBlank()) {
            throw new Impossible("a voided citation says why");
        }
        move(CitationState.VOID, on, reason);
    }

    /** The citation as the office reads it out over the telephone. */
    public List<String> lines() {
        List<String> found = new ArrayList<>();
        found.add(number + "  " + written + "  " + carrierName + " (" + usdot + ")");
        found.add("  " + offence + " at " + place + ", written by " + officer);
        if (permit != null && !permit.isEmpty()) {
            found.add("  under permit " + permit);
        }
        assessed.lines().forEach(one -> found.add("  " + one));
        found.add("  " + state);
        return List.copyOf(found);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Citation that
                && number.equals(that.number)
                && written.equals(that.written)
                && filed.equals(that.filed)
                && officer.equals(that.officer)
                && usdot == that.usdot
                && carrierName.equals(that.carrierName)
                && offence == that.offence
                && place.equals(that.place)
                && assessed.equals(that.assessed)
                && Objects.equals(permit, that.permit)
                && Objects.equals(over, that.over)
                && note.equals(that.note)
                && state == that.state;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                number,
                written,
                filed,
                officer,
                usdot,
                carrierName,
                offence,
                place,
                assessed,
                permit,
                over,
                note,
                state);
    }

    /** The one line form a list needs: {@code E-25-0417 Bickford Heavy Haul overweight $580.00 written}. */
    @Override
    public String toString() {
        return number + " " + carrierName + " " + offence + " " + amount() + " " + state;
    }

    /** The fields of a citation as the officer's pad and the office's file carry them. */
    public static final class Builder {

        private String number;
        private LocalDate written;
        private LocalDate filed;
        private String officer;
        private int usdot;
        private String carrierName;
        private Offence offence;
        private String place;
        private Assessed assessed;
        private String permit;
        private Pounds over;
        private String note = "";
        private CitationState state = CitationState.WRITTEN;

        private Builder() {}

        public Builder number(String value) {
            this.number = value;
            return this;
        }

        public Builder written(LocalDate value) {
            this.written = value;
            return this;
        }

        public Builder filed(LocalDate value) {
            this.filed = value;
            return this;
        }

        public Builder officer(String value) {
            this.officer = value;
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

        public Builder offence(Offence value) {
            this.offence = value;
            return this;
        }

        public Builder place(String value) {
            this.place = value;
            return this;
        }

        public Builder assessed(Assessed value) {
            this.assessed = value;
            return this;
        }

        /** The permit the load was stopped under; null or never set for none. */
        public Builder permit(String value) {
            this.permit = value;
            return this;
        }

        /** By how much it was over; null or never set for a flat offence. */
        public Builder over(Pounds value) {
            this.over = value;
            return this;
        }

        public Builder note(String value) {
            this.note = value;
            return this;
        }

        /** Where it stands, for a citation read back off the file. Written when never set. */
        public Builder state(CitationState value) {
            this.state = value;
            return this;
        }

        /** @throws Impossible when the citation contradicts itself */
        public Citation build() {
            return new Citation(this);
        }
    }
}
