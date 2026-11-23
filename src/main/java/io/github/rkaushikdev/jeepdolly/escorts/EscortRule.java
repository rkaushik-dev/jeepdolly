package io.github.rkaushikdev.jeepdolly.escorts;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import io.github.rkaushikdev.jeepdolly.domain.SizeLine;

/**
 * One line of the escort table: a measure past a threshold puts somebody at one place on the move.
 *
 * <p>The published table is a list of these lines and it reads the same way: width over twelve feet,
 * a car in front, not on a divided highway. A line that names a car in front for the oncoming
 * traffic says so with {@code undividedOnly}, because on a divided highway there is no oncoming
 * traffic to warn.
 *
 * @param line the measure and the size the line is drawn at
 * @param post where the line puts somebody
 * @param undividedOnly the line does not fire on a trip that runs only on divided highway
 * @param cars how many pilot cars a front or rear line puts there; one for every other line
 */
public record EscortRule(SizeLine line, Post post, boolean undividedOnly, int cars) {

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
        if (cars < 1 || (cars > 1 && post != Post.FRONT && post != Post.REAR)) {
            throw new Impossible("a line puts one car or more in front or behind, and one of anything else");
        }
    }

    /** A line that fires on any road. */
    public static EscortRule anyRoad(String measure, Inches over, Post post) {
        return new EscortRule(new SizeLine(measure, over), post, false, 1);
    }

    /** A line that fires only where the trip meets oncoming traffic. */
    public static EscortRule undivided(String measure, Inches over, Post post) {
        return undivided(measure, over, post, 1);
    }

    /** A line that puts {@code cars} pilot cars in one place where the trip meets oncoming traffic. */
    public static EscortRule undivided(String measure, Inches over, Post post, int cars) {
        return new EscortRule(new SizeLine(measure, over), post, true, cars);
    }

    /** Whether this load, on a trip that is or is not divided highway throughout, is past the line. */
    public boolean fires(Envelope envelope, boolean dividedOnly) {
        return !(undividedOnly && dividedOnly) && line.past(envelope);
    }

    /** The reason as the escorts list it: {@code rear overhang over 10 ft}. */
    public String reason() {
        return line.reason();
    }
}
