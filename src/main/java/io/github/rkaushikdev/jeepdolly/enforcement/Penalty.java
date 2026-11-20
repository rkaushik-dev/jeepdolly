package io.github.rkaushikdev.jeepdolly.enforcement;

import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Pounds;
import java.util.Optional;

/**
 * What a violation costs, which is a table and an argument about the table.
 *
 * <p>Weight is priced by the pound over, in bands, and the bands are cumulative. That word does the
 * work. A load twelve thousand pounds over is not priced at the twelve thousand pound rate for all
 * twelve thousand. It is priced at the first band rate for the first five thousand, the second
 * band rate for the next five, and the third for the last two. People get this wrong in both
 * directions and the office has had to send two letters explaining it.
 *
 * <p>A second offence inside a year doubles. A third trebles, and there it stops: the statute caps
 * the multiplier at three, and a carrier with four citations in a year has lost its registration
 * long before the fourth is priced.
 */
public final class Penalty {

    /** The most the repeat multiplier ever reaches, whatever the count. */
    public static final int MOST_REPEATS = 3;

    /** How far back the office looks when counting a repeat. */
    public static final int REPEAT_DAYS = 365;

    private Penalty() {}

    /** The overweight penalty, walking the bands cumulatively. Nothing over costs nothing. */
    public static Dollars byWeight(PenaltySchedule schedule, Pounds over) {
        if (over.value() <= 0) {
            return Dollars.of(0);
        }
        Pounds standing = over;
        Dollars amount = Dollars.of(0);
        for (Band band : schedule.bands()) {
            if (standing.value() <= 0) {
                break;
            }
            Pounds inside = band.covers(standing);
            amount = amount.plus(new Dollars((long) inside.value() * band.centsPerPound()));
            standing = new Pounds(standing.value() - inside.value());
        }
        return amount;
    }

    /**
     * One for a first offence, two for a second, three for anything after.
     *
     * @throws Impossible for a negative count of prior offences
     */
    public static int repeatsMultiplier(int prior) {
        if (prior < 0) {
            throw new Impossible("a count of prior offences is not negative");
        }
        return Math.min(prior + 1, MOST_REPEATS);
    }

    /** A first flat offence, priced off the table. */
    public static Assessed assess(PenaltySchedule schedule, Offence offence) {
        return assess(schedule, offence, Optional.empty(), 0);
    }

    /** A flat offence with prior offences in the year. */
    public static Assessed assess(PenaltySchedule schedule, Offence offence, int prior) {
        return assess(schedule, offence, Optional.empty(), prior);
    }

    /** A first offence with the overage the officer wrote in the box. */
    public static Assessed assess(PenaltySchedule schedule, Offence offence, Pounds over) {
        return assess(schedule, offence, Optional.of(over), 0);
    }

    /** An offence with the overage the officer wrote in the box and prior offences in the year. */
    public static Assessed assess(PenaltySchedule schedule, Offence offence, Pounds over, int prior) {
        return assess(schedule, offence, Optional.of(over), prior);
    }

    /**
     * Price one offence against one schedule, repeats included.
     *
     * <p>An overweight citation with no overage on it is a mistake at the roadside rather than a
     * free one, so it is refused. Everything else ignores the overage if one is passed, which
     * happens because the enforcement officer's form has one box and fills it in whatever the
     * offence.
     *
     * @throws Impossible for an overweight citation that does not say by how much
     * @throws io.github.rkaushikdev.jeepdolly.NotOnFile for a flat offence the schedule does not
     *     price
     */
    public static Assessed assess(PenaltySchedule schedule, Offence offence, Optional<Pounds> over, int prior) {
        Dollars base;
        Optional<Pounds> kept = over;
        if (offence.byWeight()) {
            if (over.isEmpty()) {
                throw new Impossible("an overweight citation says by how much");
            }
            base = byWeight(schedule, over.get());
        } else {
            base = schedule.flatFor(offence);
            kept = Optional.empty();
        }
        if (base.compareTo(schedule.minimum()) < 0) {
            base = schedule.minimum();
        }
        int multiplier = repeatsMultiplier(prior);
        return new Assessed(offence, base, multiplier, base.times(multiplier), kept);
    }
}
