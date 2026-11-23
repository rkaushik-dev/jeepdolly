package io.github.rkaushikdev.jeepdolly.escorts;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;

/**
 * One line of the escort table: a measure past a threshold puts somebody at one place on the move.
 *
 * <p>The published table is a list of these lines and it reads the same way: width over twelve feet,
 * a car in front, not on a divided highway. A line that names a car in front for the oncoming
 * traffic says so with {@code undividedOnly}, because on a divided highway there is no oncoming
 * traffic to warn.
 *
 * @param measure the measure of the envelope the line reads, as {@link Envelope#MEASURES} names it
 * @param over the line fires for a load past this, not at it
 * @param post where the line puts somebody
 * @param undividedOnly the line does not fire on a trip that runs only on divided highway
 */
public record EscortRule(String measure, Inches over, Post post, boolean undividedOnly) {

    /**
     * Where a line of the table puts somebody. A pole car is a front car with a height pole on it,
     * and a survey puts a pole car over the route before the move rather than with it.
     */
    public enum Post {
        FRONT,
        REAR,
        POLE,
        POLICE,
        SURVEY
    }

    public EscortRule {
        if (!Envelope.MEASURES.contains(measure)) {
            throw new Impossible("an envelope has no " + measure);
        }
        if (over.value() <= 0) {
            throw new Impossible("an escort threshold is a size past nothing");
        }
    }

    /** A line that fires on any road. */
    public static EscortRule anyRoad(String measure, Inches over, Post post) {
        return new EscortRule(measure, over, post, false);
    }

    /** A line that fires only where the trip meets oncoming traffic. */
    public static EscortRule undivided(String measure, Inches over, Post post) {
        return new EscortRule(measure, over, post, true);
    }

    /** Whether this load, on a trip that is or is not divided highway throughout, is past the line. */
    public boolean fires(Envelope envelope, boolean dividedOnly) {
        if (undividedOnly && dividedOnly) {
            return false;
        }
        return envelope.measure(measure).compareTo(over) > 0;
    }

    /** The reason as the escorts list it: {@code rear overhang over 10 ft}. */
    public String reason() {
        return measure.replace('_', ' ') + " over " + over;
    }
}
