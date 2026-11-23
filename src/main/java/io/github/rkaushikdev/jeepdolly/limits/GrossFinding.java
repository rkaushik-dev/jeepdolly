package io.github.rkaushikdev.jeepdolly.limits;

import io.github.rkaushikdev.jeepdolly.Pounds;

/**
 * Everything on the road at once against the gross limit for the road, seasonal order applied.
 *
 * @param limit the gross limit the reading was held to
 */
public record GrossFinding(Pounds gross, Pounds limit) implements Overage {

    @Override
    public Pounds over() {
        return gross.over(limit);
    }

    /** As a line of the letter: {@code gross 104,000 lb is 4,000 lb over 100,000 lb}. */
    @Override
    public String toString() {
        return "gross " + gross + " is " + over() + " over " + limit;
    }
}
