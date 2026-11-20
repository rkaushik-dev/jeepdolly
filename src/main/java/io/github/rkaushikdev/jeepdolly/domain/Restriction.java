package io.github.rkaushikdev.jeepdolly.domain;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.Pounds;
import java.time.LocalDate;
import java.util.Optional;

/**
 * One thing wrong with one segment, for a while or for good.
 *
 * <p>The road log changes twice a year and restrictions change every week, so they are kept apart
 * from it and joined by segment ident. Each kind carries the one thing it is compared against: a
 * clearance its height, a width restriction its width, a posted structure its weight, a seasonal
 * restriction the region its thaw order names, a curfew its clock. A closure carries nothing, and
 * no load of any size gets past it.
 *
 * <p>Everything here answers {@link #appliesOn} for a date and nothing here decides anything about
 * a load. Keeping the deciding out is what lets the same restriction be read by the router, the fee
 * schedule and the printed conditions.
 */
public record Restriction(
        String ident,
        String segment,
        RestrictionKind kind,
        String note,
        Optional<Inches> height,
        Optional<Inches> width,
        Optional<Pounds> weight,
        Optional<String> region,
        Optional<Curfew> curfew,
        Optional<LocalDate> effective,
        Optional<LocalDate> expires) {

    public Restriction {
        boolean carried =
                switch (kind) {
                    case CLEARANCE -> height.isPresent();
                    case WIDTH -> width.isPresent();
                    case WEIGHT -> weight.isPresent();
                    case SEASONAL -> region.isPresent();
                    case CURFEW -> curfew.isPresent();
                    case CLOSURE -> true;
                };
        if (!carried) {
            throw new Impossible("a " + kind + " restriction carries a " + kind + " to compare against");
        }
        if (effective.isPresent() && expires.isPresent() && expires.get().isBefore(effective.get())) {
            throw new Impossible("restriction " + ident + " lifts before it takes effect");
        }
    }

    /** A permanent restriction that carries nothing to compare against, which only a closure may. */
    public Restriction(String ident, String segment, RestrictionKind kind, String note) {
        this(builder(ident, segment, kind, note));
    }

    private Restriction(Builder built) {
        this(
                built.ident,
                built.segment,
                built.kind,
                built.note,
                Optional.ofNullable(built.height),
                Optional.ofNullable(built.width),
                Optional.ofNullable(built.weight),
                Optional.ofNullable(built.region),
                Optional.ofNullable(built.curfew),
                Optional.ofNullable(built.effective),
                Optional.ofNullable(built.expires));
    }

    /** Start a restriction; the limit it carries and its dates are set on the builder. */
    public static Builder builder(String ident, String segment, RestrictionKind kind, String note) {
        return new Builder(ident, segment, kind, note);
    }

    /** Whether the restriction is in force on a day: on or after it takes effect, not after it lifts. */
    public boolean appliesOn(LocalDate day) {
        if (effective.isPresent() && day.isBefore(effective.get())) {
            return false;
        }
        return expires.isEmpty() || !day.isAfter(expires.get());
    }

    /** No dates at all: it has always applied and it always will. */
    public boolean permanent() {
        return effective.isEmpty() && expires.isEmpty();
    }

    /** As the calendar lists it: {@code US2-1 clearance 13 ft 9 in: Norridgewock railroad bridge}. */
    @Override
    public String toString() {
        String limit = height.map(Object::toString)
                .or(() -> width.map(Object::toString))
                .or(() -> weight.map(Object::toString))
                .or(() -> curfew.map(Object::toString))
                .or(() -> region.filter(one -> !one.isEmpty()))
                .orElse("");
        String joined = limit.isEmpty() ? "" : " " + limit;
        return segment + " " + kind + joined + ": " + note;
    }

    /** The optional parts of a restriction, set by name the way the order is typed in. */
    public static final class Builder {

        private final String ident;
        private final String segment;
        private final RestrictionKind kind;
        private final String note;
        private Inches height;
        private Inches width;
        private Pounds weight;
        private String region;
        private Curfew curfew;
        private LocalDate effective;
        private LocalDate expires;

        private Builder(String ident, String segment, RestrictionKind kind, String note) {
            this.ident = ident;
            this.segment = segment;
            this.kind = kind;
            this.note = note;
        }

        public Builder height(Inches value) {
            this.height = value;
            return this;
        }

        public Builder width(Inches value) {
            this.width = value;
            return this;
        }

        public Builder weight(Pounds value) {
            this.weight = value;
            return this;
        }

        public Builder region(String value) {
            this.region = value;
            return this;
        }

        public Builder curfew(Curfew value) {
            this.curfew = value;
            return this;
        }

        public Builder effective(LocalDate value) {
            this.effective = value;
            return this;
        }

        public Builder expires(LocalDate value) {
            this.expires = value;
            return this;
        }

        /** @throws Impossible when the kind's limit is missing or the dates run backwards */
        public Restriction build() {
            return new Restriction(this);
        }
    }
}
