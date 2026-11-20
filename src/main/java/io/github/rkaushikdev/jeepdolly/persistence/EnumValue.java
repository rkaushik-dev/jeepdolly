package io.github.rkaushikdev.jeepdolly.persistence;

import io.github.rkaushikdev.jeepdolly.SeasonKind;
import io.github.rkaushikdev.jeepdolly.Valued;
import io.github.rkaushikdev.jeepdolly.domain.AxleKind;
import io.github.rkaushikdev.jeepdolly.domain.Billing;
import io.github.rkaushikdev.jeepdolly.domain.Family;
import io.github.rkaushikdev.jeepdolly.domain.HighwayClass;
import io.github.rkaushikdev.jeepdolly.domain.RestrictionKind;
import io.github.rkaushikdev.jeepdolly.domain.UnitKind;
import io.github.rkaushikdev.jeepdolly.enforcement.CitationState;
import io.github.rkaushikdev.jeepdolly.enforcement.Offence;
import io.github.rkaushikdev.jeepdolly.permits.ApplicationState;
import io.github.rkaushikdev.jeepdolly.permits.PermitState;
import io.github.rkaushikdev.jeepdolly.permits.Reason;
import io.github.rkaushikdev.jeepdolly.structures.ReviewState;
import io.github.rkaushikdev.jeepdolly.structures.StructureKind;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * An enumeration, stored as its short string value: not a database enum type and not an ordinal.
 *
 * <p>Rows have to be readable in a console at two in the morning, and adding a class of road
 * should not be a migration. One converter per stored enumeration, each applied wherever that type
 * appears on a row, and each reading back through the enumeration's own {@link Valued} lookup, so a
 * value nobody knows is refused rather than read as something else.
 *
 * @param <E> the enumeration
 */
public abstract class EnumValue<E extends Enum<E> & Valued> implements AttributeConverter<E, String> {

    private final Class<E> type;

    protected EnumValue(Class<E> type) {
        this.type = type;
    }

    @Override
    public String convertToDatabaseColumn(E value) {
        return value == null ? null : value.value();
    }

    @Override
    public E convertToEntityAttribute(String value) {
        return value == null ? null : Valued.of(type, value);
    }

    /** How a carrier pays, as its short value. */
    @Converter(autoApply = true)
    public static final class Billings extends EnumValue<Billing> {
        public Billings() {
            super(Billing.class);
        }
    }

    /** Where an axle sits, as its short value. */
    @Converter(autoApply = true)
    public static final class AxleKinds extends EnumValue<AxleKind> {
        public AxleKinds() {
            super(AxleKind.class);
        }
    }

    /** A class of road, as its short value. */
    @Converter(autoApply = true)
    public static final class HighwayClasses extends EnumValue<HighwayClass> {
        public HighwayClasses() {
            super(HighwayClass.class);
        }
    }

    /** A piece of equipment, as its short value. */
    @Converter(autoApply = true)
    public static final class UnitKinds extends EnumValue<UnitKind> {
        public UnitKinds() {
            super(UnitKind.class);
        }
    }

    /** A commodity family, as its short value. */
    @Converter(autoApply = true)
    public static final class Families extends EnumValue<Family> {
        public Families() {
            super(Family.class);
        }
    }

    /** A kind of restriction, as its short value. */
    @Converter(autoApply = true)
    public static final class RestrictionKinds extends EnumValue<RestrictionKind> {
        public RestrictionKinds() {
            super(RestrictionKind.class);
        }
    }

    /** A kind of structure, as its short value. */
    @Converter(autoApply = true)
    public static final class StructureKinds extends EnumValue<StructureKind> {
        public StructureKinds() {
            super(StructureKind.class);
        }
    }

    /** Where a review stands, as its short value. */
    @Converter(autoApply = true)
    public static final class ReviewStates extends EnumValue<ReviewState> {
        public ReviewStates() {
            super(ReviewState.class);
        }
    }

    /** A kind of seasonal order, as its short value. */
    @Converter(autoApply = true)
    public static final class SeasonKinds extends EnumValue<SeasonKind> {
        public SeasonKinds() {
            super(SeasonKind.class);
        }
    }

    /** Where a citation stands, as its short value. */
    @Converter(autoApply = true)
    public static final class CitationStates extends EnumValue<CitationState> {
        public CitationStates() {
            super(CitationState.class);
        }
    }

    /** An offence on a citation, as its short value. */
    @Converter(autoApply = true)
    public static final class Offences extends EnumValue<Offence> {
        public Offences() {
            super(Offence.class);
        }
    }

    /** Where an application stands, as its short value. */
    @Converter(autoApply = true)
    public static final class ApplicationStates extends EnumValue<ApplicationState> {
        public ApplicationStates() {
            super(ApplicationState.class);
        }
    }

    /** Where a permit stands, as its short value. */
    @Converter(autoApply = true)
    public static final class PermitStates extends EnumValue<PermitState> {
        public PermitStates() {
            super(PermitState.class);
        }
    }

    /** Why a copy was printed, as its short value. */
    @Converter(autoApply = true)
    public static final class Reasons extends EnumValue<Reason> {
        public Reasons() {
            super(Reason.class);
        }
    }
}
