package io.github.rkaushikdev.jeepdolly.domain;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.Pounds;

/**
 * One axle, its load, and the rubber it stands on.
 *
 * <p>The road does not care what the truck weighs. It cares what is pressed into it in one place,
 * so an axle carries its own weight and the tires it stands on. What those tires may carry is the
 * table's business rather than the axle's, and {@link io.github.rkaushikdev.jeepdolly.limits.TireRule}
 * reads it: a tridem under a hundred thousand pounds on narrow rubber will make its group limit and
 * still cut the pavement, and the office refuses it on the tire rule alone.
 *
 * <p>A liftable axle is the standing argument of the permit counter. The rule the office works to
 * is that a liftable axle is assumed down and carrying its declared share.
 */
public record Axle(AxleKind kind, Pounds weight, int tires, Inches tireWidth, boolean liftable) {

    /** Four tires, unless the axle says otherwise. */
    public static final int TIRES = 4;

    /** Eleven inch rubber, unless the axle says otherwise. */
    public static final Inches TIRE_WIDTH = new Inches(11);

    public Axle {
        if (weight.value() < 0) {
            throw new Impossible("an axle cannot carry a negative weight");
        }
        if (tires < 2 || tires % 2 != 0) {
            throw new Impossible("an axle stands on an even count of tires, not " + tires);
        }
        if (tireWidth.value() < 1) {
            throw new Impossible("an axle stands on tires with a width");
        }
        if (kind == AxleKind.STEER && liftable) {
            throw new Impossible("a steer axle does not lift");
        }
    }

    /** An axle on four eleven inch tires that stays down. */
    public Axle(AxleKind kind, Pounds weight) {
        this(kind, weight, TIRES, TIRE_WIDTH, false);
    }

    /** An axle on the given rubber that stays down. */
    public Axle(AxleKind kind, Pounds weight, int tires, Inches tireWidth) {
        this(kind, weight, tires, tireWidth, false);
    }

    public Axle withTires(int count) {
        return new Axle(kind, weight, count, tireWidth, liftable);
    }

    public Axle withTireWidth(Inches width) {
        return new Axle(kind, weight, tires, width, liftable);
    }

    public Axle withLiftable(boolean lifts) {
        return new Axle(kind, weight, tires, tireWidth, lifts);
    }

    /** Total width of rubber on the ground under this axle. */
    public Inches tireInches() {
        return tireWidth.times(tires);
    }

    /** The way the counter reads it aloud: {@code drive 17,000 lb on 4 tires}. */
    @Override
    public String toString() {
        return kind + " " + weight + " on " + tires + " tires" + (liftable ? " liftable" : "");
    }
}
