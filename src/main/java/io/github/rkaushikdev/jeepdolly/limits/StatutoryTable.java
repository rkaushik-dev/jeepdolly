package io.github.rkaushikdev.jeepdolly.limits;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.domain.HighwayClass;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * One class of road, one set of statutory weight limits, one effective date.
 *
 * <p>These are the numbers a truck may carry with no permit at all. Everything the permit office
 * does is measured from them: an application is overweight if it is over one of these, the fee is
 * priced on how far over, and a route is chosen partly on which table reaches it.
 *
 * <p>A group of more axles than the table names is read as the largest group it does name plus an
 * allowance for each axle past it. That is how the published table reads and it is why a nine axle
 * trailer does not get its own line.
 *
 * @param byCount the limit for a group of each named size, from the single up
 * @param beyondTable the allowance for each axle past the largest named group
 * @param gross the limit on everything on the road at once
 * @param tireRate pounds per inch of tire on the ground
 */
public record StatutoryTable(
        HighwayClass highwayClass,
        LocalDate effective,
        Map<Integer, Pounds> byCount,
        Pounds beyondTable,
        Pounds gross,
        int tireRate) {

    public StatutoryTable {
        byCount = Collections.unmodifiableMap(new TreeMap<>(byCount));
        if (!byCount.containsKey(1) || !byCount.containsKey(2)) {
            throw new Impossible("a table names at least the single and the tandem");
        }
        List<Integer> counts = new ArrayList<>(byCount.keySet());
        for (int index = 1; index < counts.size(); index++) {
            int smaller = counts.get(index - 1);
            int larger = counts.get(index);
            if (byCount.get(larger).compareTo(byCount.get(smaller)) <= 0) {
                throw new Impossible("the " + larger + " axle limit is not above the " + smaller + " one");
            }
        }
        if (tireRate < 1) {
            throw new Impossible("a tire rate is pounds per inch and it is not nothing");
        }
    }

    /** The tire rule at this table's rate. */
    public TireRule tireRule() {
        return TireRule.at(tireRate);
    }

    /** The largest group the table names a limit for. */
    public int largestNamed() {
        return Collections.max(byCount.keySet());
    }

    /**
     * The limit for a group of this many axles.
     *
     * @throws Impossible for a group of no axles
     */
    public Pounds groupLimit(int count) {
        if (count < 1) {
            throw new Impossible("a group holds at least one axle");
        }
        Pounds named = byCount.get(count);
        if (named != null) {
            return named;
        }
        int largest = largestNamed();
        return byCount.get(largest).plus(beyondTable.times(count - largest));
    }

    /** How far a group of this many axles is over its limit, never below zero. */
    public Pounds overGroup(int count, Pounds weight) {
        return weight.over(groupLimit(count));
    }

    /** How far a gross weight is over the table's gross limit, never below zero. */
    public Pounds overGross(Pounds weight) {
        return weight.over(gross);
    }

    /** As the office names it: {@code state aid table of 2024-07-01}. */
    @Override
    public String toString() {
        return highwayClass + " table of " + effective;
    }
}
