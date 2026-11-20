package io.github.rkaushikdev.jeepdolly.persistence;

import java.time.LocalDate;

/**
 * The column lengths every table agrees on, and the one date helper the readers share.
 *
 * <p>Three decisions sit under the schema and all three are expensive to change later. Every
 * constraint is named, as a function of its table and columns ({@code pk_}, {@code uq_}, {@code
 * ix_}, {@code fk_}, {@code ck_}), so a migration that drops one can say which. Nothing is stored in
 * a type that loses information: a weight is whole pounds, a size whole inches, a distance whole
 * hundredths of a mile, money whole cents, and the converters in {@link MeasureTypes} are the only
 * way those values reach the database. Enumerations are stored as their short string value
 * ({@link EnumValue}), so a row reads in a console at two in the morning and adding a class of road
 * is not a migration.
 */
public final class Columns {

    /** Long enough for a carrier name, short enough that nobody pastes an essay. */
    public static final int NAME_LENGTH = 120;

    /** Codes, idents and references. */
    public static final int CODE_LENGTH = 32;

    /** A condition, a note, a refusal reason. */
    public static final int TEXT_LENGTH = 500;

    private Columns() {}

    /** A date as it arrives from JSON, which is as text often enough to be worth one helper. */
    public static LocalDate asDate(String value) {
        return LocalDate.parse(value);
    }

    /** A date that is already a date, unchanged. */
    public static LocalDate asDate(LocalDate value) {
        return value;
    }
}
