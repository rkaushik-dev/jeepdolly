package io.github.rkaushikdev.jeepdolly.conditions;

import io.github.rkaushikdev.jeepdolly.Valued;

/**
 * Which part of the permit face a condition prints under.
 *
 * <p>The constants are declared in printing order: a driver reads the conditions in the cab and
 * wants the ones about today near the top.
 */
public enum Category implements Valued {
    TRAVEL("travel"),
    ROUTE("route"),
    STRUCTURE("structure"),
    ESCORT("escort"),
    MARKING("marking"),
    EQUIPMENT("equipment"),
    GENERAL("general");

    private final String value;

    Category(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** The category stored under {@code value}. */
    public static Category of(String value) {
        return Valued.of(Category.class, value);
    }

    @Override
    public String toString() {
        return value;
    }
}
