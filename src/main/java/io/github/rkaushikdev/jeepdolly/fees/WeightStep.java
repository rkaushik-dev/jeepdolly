package io.github.rkaushikdev.jeepdolly.fees;

import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.Pounds;
import java.util.Locale;
import java.util.Optional;

/**
 * A band of overage on the weight charge, and what each block of overage in it costs.
 *
 * <p>The steps are the part of the schedule the trucking association argues about every session:
 * the first ten thousand pounds over costs less per thousand than the fiftieth does.
 *
 * @param upTo the top of the band in pounds over legal gross, or empty for the last band
 * @param perBlock the charge for each block of {@link FeeSchedule#WEIGHT_BLOCK} pounds in the band
 */
public record WeightStep(Optional<Pounds> upTo, Dollars perBlock) {

    /** A band with a ceiling. */
    public WeightStep(Pounds upTo, Dollars perBlock) {
        this(Optional.of(upTo), perBlock);
    }

    /** The last band, which runs on from the one before it with no ceiling. */
    public static WeightStep andAbove(Dollars perBlock) {
        return new WeightStep(Optional.empty(), perBlock);
    }

    /** The way the published table reads: {@code $5.00 per 1,000 lb to 20,000 lb}. */
    @Override
    public String toString() {
        String ceiling = upTo.map(one -> "to " + one).orElse("and above");
        return String.format(Locale.ROOT, "%s per %,d lb %s", perBlock, FeeSchedule.WEIGHT_BLOCK, ceiling);
    }
}
