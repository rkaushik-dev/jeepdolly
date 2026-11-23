package io.github.rkaushikdev.jeepdolly.limits;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.domain.Axle;
import io.github.rkaushikdev.jeepdolly.domain.AxleGroup;
import io.github.rkaushikdev.jeepdolly.domain.AxleKind;

/**
 * The tire rule: what the rubber under one axle will carry on one class of road.
 *
 * <p>The table publishes a rate in pounds per inch of tire on the ground, and for duals that is the
 * whole rule. Wide base singles are the exception. A pair of eighteen inch singles has more rubber
 * on paper than four elevens and puts it down in two narrow strips, and the pavement people have
 * the cores to show what that does. So an axle standing on two wide base singles carries the rate
 * times its rubber or {@link #WIDE_BASE_CAP}, whichever is less. A steer axle stands on two wide
 * tires by design and is never read as wide base.
 *
 * @param poundsPerInch the rate the statutory table publishes
 * @param wideBaseCap the most an axle on wide base singles carries, whatever its rubber
 */
public record TireRule(int poundsPerInch, Pounds wideBaseCap) {

    /** A single tire this wide or wider is a wide base single. */
    public static final Inches WIDE_BASE_WIDTH = new Inches(14);

    /** The most an axle on wide base singles carries. */
    public static final Pounds WIDE_BASE_CAP = new Pounds(20000);

    public TireRule {
        if (poundsPerInch < 1) {
            throw new Impossible("a tire rate is pounds per inch and it is not nothing");
        }
    }

    /** The rule at a published rate, with the usual wide base cap. */
    public static TireRule at(int poundsPerInch) {
        return new TireRule(poundsPerInch, WIDE_BASE_CAP);
    }

    /** Whether the axle stands on one wide tire at each end rather than on duals. */
    public static boolean wideBase(Axle axle) {
        return axle.kind() != AxleKind.STEER
                && axle.tires() == 2
                && axle.tireWidth().compareTo(WIDE_BASE_WIDTH) >= 0;
    }

    /** What the axle's tires will carry under this rule. */
    public Pounds limit(Axle axle) {
        Pounds rubber = new Pounds(axle.tireInches().value() * poundsPerInch);
        if (wideBase(axle) && rubber.compareTo(wideBaseCap) > 0) {
            return wideBaseCap;
        }
        return rubber;
    }

    /** How far past what its tires will carry the axle is loaded, never below zero. */
    public Pounds over(Axle axle) {
        return axle.weight().over(limit(axle));
    }

    /** What every axle of a group will carry, added up. */
    public Pounds limit(AxleGroup group) {
        Pounds total = Pounds.ZERO;
        for (Axle axle : group.axles()) {
            total = total.plus(limit(axle));
        }
        return total;
    }

    /** One axle of a stream against the rule, as the weight reading lists it. */
    public TireFinding read(int position, Axle axle) {
        return new TireFinding(position, axle.weight(), limit(axle));
    }
}
