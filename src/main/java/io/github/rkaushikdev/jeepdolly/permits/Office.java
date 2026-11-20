package io.github.rkaushikdev.jeepdolly.permits;

import io.github.rkaushikdev.jeepdolly.PermitCalendar;
import io.github.rkaushikdev.jeepdolly.SeasonBook;
import io.github.rkaushikdev.jeepdolly.conditions.ConditionLibrary;
import io.github.rkaushikdev.jeepdolly.domain.CarrierRegister;
import io.github.rkaushikdev.jeepdolly.domain.CommodityBook;
import io.github.rkaushikdev.jeepdolly.domain.Network;
import io.github.rkaushikdev.jeepdolly.domain.RestrictionBook;
import io.github.rkaushikdev.jeepdolly.enforcement.EnforcementBook;
import io.github.rkaushikdev.jeepdolly.escorts.EscortRegister;
import io.github.rkaushikdev.jeepdolly.fees.ScheduleBook;
import io.github.rkaushikdev.jeepdolly.limits.SizeBook;
import io.github.rkaushikdev.jeepdolly.limits.StatutoryBook;
import io.github.rkaushikdev.jeepdolly.routing.TurnBook;
import io.github.rkaushikdev.jeepdolly.structures.StructureBook;
import io.github.rkaushikdev.jeepdolly.utilities.LineBook;
import java.util.Objects;

/**
 * The office: every book a decision reads, wired together once.
 *
 * <p>The office is a bag of books and nothing else, so it sits on its own, apart from the procedure
 * that reads it, and the things that need one do not drag the procedure in with it. The books are
 * the live ones: adding a restriction to {@link #restrictions()} is seen by the next assessment.
 */
public record Office(
        Network network,
        RestrictionBook restrictions,
        SeasonBook seasons,
        StatutoryBook statutes,
        SizeBook sizes,
        CommodityBook commodities,
        CarrierRegister carriers,
        StructureBook structures,
        TurnBook turns,
        EscortRegister escorts,
        EnforcementBook enforcement,
        LineBook lines,
        ScheduleBook schedules,
        ConditionLibrary library,
        Numbering numbering) {

    public Office {
        Objects.requireNonNull(network, "an office reads the road log");
        Objects.requireNonNull(restrictions, "an office reads the restrictions");
        Objects.requireNonNull(seasons, "an office reads the seasonal orders");
        Objects.requireNonNull(statutes, "an office reads the statutory tables");
        Objects.requireNonNull(sizes, "an office reads the size tables");
        Objects.requireNonNull(commodities, "an office reads the commodity table");
        Objects.requireNonNull(carriers, "an office reads the carrier register");
        Objects.requireNonNull(structures, "an office reads the structure inventory");
        Objects.requireNonNull(turns, "an office reads the corner card index");
        Objects.requireNonNull(escorts, "an office reads the pilot car register");
        Objects.requireNonNull(enforcement, "an office reads the enforcement accounts");
        Objects.requireNonNull(lines, "an office reads the overhead lines");
        Objects.requireNonNull(schedules, "an office reads the fee schedules");
        Objects.requireNonNull(library, "an office reads the condition library");
        Objects.requireNonNull(numbering, "an office hands out permit numbers");
    }

    /** Start an office; every book is named on the builder. */
    public static Builder builder() {
        return new Builder();
    }

    /** The office and road calendars of one year. */
    public PermitCalendar calendar(int year) {
        return new PermitCalendar(year);
    }

    /** The same office reading a different set of enforcement accounts. */
    public Office withEnforcement(EnforcementBook accounts) {
        return toBuilder().enforcement(accounts).build();
    }

    /** A builder holding every book this office reads, to change one and build again. */
    public Builder toBuilder() {
        return builder()
                .network(network)
                .restrictions(restrictions)
                .seasons(seasons)
                .statutes(statutes)
                .sizes(sizes)
                .commodities(commodities)
                .carriers(carriers)
                .structures(structures)
                .turns(turns)
                .escorts(escorts)
                .enforcement(enforcement)
                .lines(lines)
                .schedules(schedules)
                .library(library)
                .numbering(numbering);
    }

    /** The books of an office, named one at a time, every one of them required. */
    public static final class Builder {

        private Network network;
        private RestrictionBook restrictions;
        private SeasonBook seasons;
        private StatutoryBook statutes;
        private SizeBook sizes;
        private CommodityBook commodities;
        private CarrierRegister carriers;
        private StructureBook structures;
        private TurnBook turns;
        private EscortRegister escorts;
        private EnforcementBook enforcement;
        private LineBook lines;
        private ScheduleBook schedules;
        private ConditionLibrary library;
        private Numbering numbering;

        private Builder() {}

        public Builder network(Network value) {
            this.network = value;
            return this;
        }

        public Builder restrictions(RestrictionBook value) {
            this.restrictions = value;
            return this;
        }

        public Builder seasons(SeasonBook value) {
            this.seasons = value;
            return this;
        }

        public Builder statutes(StatutoryBook value) {
            this.statutes = value;
            return this;
        }

        public Builder sizes(SizeBook value) {
            this.sizes = value;
            return this;
        }

        public Builder commodities(CommodityBook value) {
            this.commodities = value;
            return this;
        }

        public Builder carriers(CarrierRegister value) {
            this.carriers = value;
            return this;
        }

        public Builder structures(StructureBook value) {
            this.structures = value;
            return this;
        }

        public Builder turns(TurnBook value) {
            this.turns = value;
            return this;
        }

        public Builder escorts(EscortRegister value) {
            this.escorts = value;
            return this;
        }

        public Builder enforcement(EnforcementBook value) {
            this.enforcement = value;
            return this;
        }

        public Builder lines(LineBook value) {
            this.lines = value;
            return this;
        }

        public Builder schedules(ScheduleBook value) {
            this.schedules = value;
            return this;
        }

        public Builder library(ConditionLibrary value) {
            this.library = value;
            return this;
        }

        public Builder numbering(Numbering value) {
            this.numbering = value;
            return this;
        }

        /** @throws NullPointerException naming the first book that was not given */
        public Office build() {
            return new Office(
                    network,
                    restrictions,
                    seasons,
                    statutes,
                    sizes,
                    commodities,
                    carriers,
                    structures,
                    turns,
                    escorts,
                    enforcement,
                    lines,
                    schedules,
                    library,
                    numbering);
        }
    }
}
