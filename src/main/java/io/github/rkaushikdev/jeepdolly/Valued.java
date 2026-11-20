package io.github.rkaushikdev.jeepdolly;

/**
 * An enumeration stored and printed as its short string value rather than its name.
 *
 * <p>Rows have to be readable in a console at two in the morning, so a class of road is stored as
 * {@code state aid} and not as an ordinal or a constant name. Every enumeration in the office's
 * vocabulary implements this, prints as its value, and is looked up by it.
 */
public interface Valued {

    /** The short value this constant is stored and printed under. */
    String value();

    /**
     * The constant of {@code type} stored under {@code value}.
     *
     * @throws IllegalArgumentException when no constant is stored under it
     */
    static <E extends Enum<E> & Valued> E of(Class<E> type, String value) {
        for (E constant : type.getEnumConstants()) {
            if (constant.value().equals(value)) {
                return constant;
            }
        }
        throw new IllegalArgumentException("'" + value + "' is not a valid " + type.getSimpleName());
    }
}
