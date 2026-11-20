package io.github.rkaushikdev.jeepdolly.persistence;

import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.Miles;
import io.github.rkaushikdev.jeepdolly.Pounds;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * The measures, stored as the whole numbers they hold and handed back as the type they went in as.
 *
 * <p>No column in this schema is a float, and these converters are the only way a measure reaches
 * the database, so nobody can add one by accident. A null column comes back as no measure at all,
 * never as a zero.
 */
public final class MeasureTypes {

    private MeasureTypes() {}

    /** A weight, stored as a whole number of pounds. */
    @Converter(autoApply = true)
    public static final class PoundsType implements AttributeConverter<Pounds, Integer> {

        @Override
        public Integer convertToDatabaseColumn(Pounds value) {
            return value == null ? null : value.value();
        }

        @Override
        public Pounds convertToEntityAttribute(Integer value) {
            return value == null ? null : new Pounds(value);
        }
    }

    /** A size, stored as whole inches. */
    @Converter(autoApply = true)
    public static final class InchesType implements AttributeConverter<Inches, Integer> {

        @Override
        public Integer convertToDatabaseColumn(Inches value) {
            return value == null ? null : value.value();
        }

        @Override
        public Inches convertToEntityAttribute(Integer value) {
            return value == null ? null : new Inches(value);
        }
    }

    /** A distance, stored as whole hundredths of a mile. */
    @Converter(autoApply = true)
    public static final class MilesType implements AttributeConverter<Miles, Integer> {

        @Override
        public Integer convertToDatabaseColumn(Miles value) {
            return value == null ? null : value.hundredths();
        }

        @Override
        public Miles convertToEntityAttribute(Integer value) {
            return value == null ? null : new Miles(value);
        }
    }

    /** Money, stored as whole cents. */
    @Converter(autoApply = true)
    public static final class CentsType implements AttributeConverter<Dollars, Long> {

        @Override
        public Long convertToDatabaseColumn(Dollars value) {
            return value == null ? null : value.cents();
        }

        @Override
        public Dollars convertToEntityAttribute(Long value) {
            return value == null ? null : new Dollars(value);
        }
    }
}
