package io.github.rkaushikdev.jeepdolly.enforcement;

import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.Pounds;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What one citation comes to, with the working shown.
 *
 * @param base the amount before the repeat multiplier, the schedule minimum already applied
 * @param total the base times the multiplier, which is what is due
 * @param over the overage the amount was read off, for an overweight citation only
 */
public record Assessed(Offence offence, Dollars base, int multiplier, Dollars total, Optional<Pounds> over) {

    /** An assessment that was not read off a scale. */
    public Assessed(Offence offence, Dollars base, int multiplier, Dollars total) {
        this(offence, base, multiplier, total, Optional.empty());
    }

    /** The working, as the citation prints it under the offence. */
    public List<String> lines() {
        List<String> found = new ArrayList<>();
        found.add(offence + "  " + base);
        over.ifPresent(pounds -> found.add("  over by  " + pounds));
        if (multiplier > 1) {
            found.add("  repeat offence, times " + multiplier);
        }
        found.add("  due  " + total);
        return List.copyOf(found);
    }

    @Override
    public String toString() {
        return offence + ": " + total;
    }
}
