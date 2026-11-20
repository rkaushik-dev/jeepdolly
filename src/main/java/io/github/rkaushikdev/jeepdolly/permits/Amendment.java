package io.github.rkaushikdev.jeepdolly.permits;

import io.github.rkaushikdev.jeepdolly.fees.Quote;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The result of an amendment: two permits, a price and a list of what moved.
 *
 * <p>Both permits are kept forever: the replaced one marked superseded and pointing at its
 * replacement, the replacement pointing back.
 *
 * @param original the permit being replaced, now superseded
 * @param replacement the permit issued in its place, in the replacement series
 * @param changes what the revised application moved, in the order the letter lists it
 * @param quote what the amendment costs: the amendment fee and any difference in permit fee
 * @param assessment the fresh assessment the replacement was written from
 */
public record Amendment(Permit original, Permit replacement, List<Change> changes, Quote quote, Assessment assessment) {

    public Amendment {
        changes = List.copyOf(changes);
    }

    /** As the file notes it: {@code 25-P-000001 to 25-R-000001, dates changed}. */
    @Override
    public String toString() {
        String moved = changes.stream().map(Change::what).collect(Collectors.joining(", "));
        return original.number() + " to " + replacement.number() + ", " + (moved.isEmpty() ? "nothing" : moved)
                + " changed";
    }
}
