package io.github.rkaushikdev.jeepdolly.domain;

import io.github.rkaushikdev.jeepdolly.Valued;

/**
 * The four rule sets that decide which limits reach a road, ordered by what they will carry.
 *
 * <p>The interstate is federal: the state may not raise a limit on it. An arterial is a numbered
 * through route off the interstate, and carries the permit traffic. A state aid road is a numbered
 * secondary the state maintains but did not build to the same standard; most posted structures and
 * most thaw orders are on these. A town way is maintained by the town, and the state cannot write a
 * permit for one at all, which is the class that surprises people: a mill yard on a town way needs
 * two permits from two governments.
 */
public enum HighwayClass implements Valued {
    INTERSTATE("interstate"),
    ARTERIAL("arterial"),
    STATE_AID("state aid"),
    TOWN_WAY("town way");

    private final String value;

    HighwayClass(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** The constant stored under {@code value}. */
    public static HighwayClass of(String value) {
        return Valued.of(HighwayClass.class, value);
    }

    public boolean stateMaintained() {
        return this != TOWN_WAY;
    }

    /** Whether this office can write a permit over it at all. */
    public boolean permittable() {
        return stateMaintained();
    }

    public boolean federal() {
        return this == INTERSTATE;
    }

    @Override
    public String toString() {
        return value;
    }
}
