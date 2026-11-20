package io.github.rkaushikdev.jeepdolly.enforcement;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Pounds;
import java.util.Optional;

/**
 * One step of the overweight scale, priced in cents per pound.
 *
 * @param upto how many pounds of the overage this band takes, or empty for the last band, which
 *     runs to the top
 */
public record Band(Optional<Pounds> upto, int centsPerPound) {

    public Band {
        if (centsPerPound <= 0) {
            throw new Impossible("a penalty band charges something");
        }
    }

    /** A band that takes at most {@code upto} pounds of the overage. */
    public Band(Pounds upto, int centsPerPound) {
        this(Optional.of(upto), centsPerPound);
    }

    /** The last band, which takes whatever overage is left. */
    public static Band toTheTop(int centsPerPound) {
        return new Band(Optional.empty(), centsPerPound);
    }

    /** How much of this overage falls in this band. */
    public Pounds covers(Pounds over) {
        return upto.map(ceiling -> over.compareTo(ceiling) < 0 ? over : ceiling).orElse(over);
    }

    /** The way the statute reads: {@code to 5,000 lb at 3c a pound}. */
    @Override
    public String toString() {
        String ceiling = upto.map(one -> "to " + one).orElse("and over");
        return ceiling + " at " + centsPerPound + "c a pound";
    }
}
