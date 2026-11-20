package io.github.rkaushikdev.jeepdolly.limits;

import io.github.rkaushikdev.jeepdolly.Valued;

/**
 * The rung of the permit ladder, in the order the ladder is walked, which decides who may issue it.
 *
 * <p>None: inside legal size and legal weight, and the clerk says so. Ordinary: past legal, inside
 * the routine boxes, under the superload thresholds; a clerk issues it. Study: past the routine box
 * on size, and an engineer reads it before anybody issues anything. Superload: past the thresholds
 * the state publishes for the word, which pulls in a structural review of every bridge on the route
 * and is the only kind that can be refused outright on the strength of one span.
 */
public enum PermitKind implements Valued {
    NONE("none"),
    ORDINARY("ordinary"),
    STUDY("study"),
    SUPERLOAD("superload");

    private final String value;

    PermitKind(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** The constant stored under {@code value}. */
    public static PermitKind of(String value) {
        return Valued.of(PermitKind.class, value);
    }

    /** Whether an engineer reads it before it is issued. */
    public boolean needsEngineer() {
        return this == STUDY || this == SUPERLOAD;
    }

    /** Whether a clerk may issue it while the hauler waits. */
    public boolean issuableAtTheCounter() {
        return this == ORDINARY;
    }

    @Override
    public String toString() {
        return value;
    }
}
