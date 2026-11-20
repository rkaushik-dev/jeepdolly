package io.github.rkaushikdev.jeepdolly.structures;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Pounds;
import java.util.List;
import java.util.Objects;

/**
 * One load over one structure, with the factor that decided it.
 *
 * <p>The rating factor is capacity over demand, in whole percent, because the inspection reports
 * come in whole percent and a rating carried to four decimal places invites an argument about the
 * fourth. A factor of 112 means the operating rating is twelve percent above what this load asks
 * of it.
 *
 * <p>Three bands and they are the whole of the engineering judgement here. At {@link
 * #CLEAR_PERCENT} or above the load crosses normally. Between {@link #REFUSE_PERCENT} and that it
 * crosses under three conditions: alone on the structure, at walking pace, centred over the girders
 * that were designed to take it. Below {@link #REFUSE_PERCENT} it does not cross.
 *
 * <p>A posting beats all three. If somebody signed an order posting the structure, the sign is the
 * limit whatever the arithmetic says, until somebody signs another order.
 *
 * @param gross the weight the structure feels, which is the governing window rather than the
 *     whole train
 * @param factor operating rating over the load in whole percent, or zero when posted out
 * @param postedOut the sign refused the load before any arithmetic was done
 */
public record Rating(Structure structure, Pounds gross, int factor, Verdict verdict, boolean postedOut) {

    /** Factor at or above which a load crosses with no conditions. */
    public static final int CLEAR_PERCENT = 110;

    /** Factor below which no condition will make the crossing acceptable. */
    public static final int REFUSE_PERCENT = 100;

    /** Postings are set from the inventory rating, rounded down to this step. */
    public static final int POSTING_STEP = 1000;

    /** A rating the arithmetic decided. */
    public Rating(Structure structure, Pounds gross, int factor, Verdict verdict) {
        this(structure, gross, factor, verdict, false);
    }

    /**
     * Operating rating over the load, in whole percent, rounded down.
     *
     * @throws Impossible for a load of no weight, or a structure with no operating rating
     */
    public static int factorFor(Structure structure, Pounds gross) {
        if (gross.value() <= 0) {
            throw new Impossible("a load with no weight cannot be rated");
        }
        Pounds capacity = structure
                .operatingRating()
                .orElseThrow(() -> new Impossible(structure.ident() + " has no operating rating to rate against"));
        return Math.floorDiv(capacity.value() * 100, gross.value());
    }

    /** Rate a load over a structure, posting first and arithmetic second. */
    public static Rating rate(Structure structure, Pounds gross) {
        if (!structure.carries(gross)) {
            return new Rating(structure, gross, 0, Verdict.REFUSED, true);
        }
        int factor = factorFor(structure, gross);
        Verdict verdict;
        if (factor >= CLEAR_PERCENT) {
            verdict = Verdict.CLEARS;
        } else if (factor >= REFUSE_PERCENT) {
            verdict = Verdict.CONDITIONAL;
        } else {
            verdict = Verdict.REFUSED;
        }
        return new Rating(structure, gross, factor, verdict);
    }

    /**
     * What the sign would read if it were set from the inventory rating.
     *
     * @throws Impossible for a structure with no inventory rating
     */
    public static Pounds postingFor(Structure structure) {
        Pounds rating = structure
                .inventoryRating()
                .orElseThrow(() -> new Impossible(structure.ident() + " has no inventory rating to post from"));
        return new Pounds(Math.floorDiv(rating.value(), POSTING_STEP) * POSTING_STEP);
    }

    /** Whether the sign agrees with the rating the last inspection produced. */
    public static boolean postingIsCurrent(Structure structure) {
        if (structure.posted().isEmpty() || structure.inventoryRating().isEmpty()) {
            return true;
        }
        return Objects.equals(structure.posted().get(), postingFor(structure));
    }

    /** The operating rating, or nothing for a structure that carries no traffic. */
    public Pounds capacity() {
        return structure.operatingRating().orElse(Pounds.ZERO);
    }

    /**
     * What has to be true for a conditional crossing to be acceptable, in the order the permit
     * prints them. Nothing for a rating that clears or refuses.
     */
    public List<String> crossingConditions() {
        if (verdict != Verdict.CONDITIONAL) {
            return List.of();
        }
        String name = structure.name();
        return List.of(
                "cross " + name + " alone, no other vehicle on the structure",
                "cross " + name + " at walking pace",
                "cross " + name + " centred on the roadway");
    }

    /** As the worksheet prints it: {@code 1247 rates 140% for 94,000 lb, clears}. */
    @Override
    public String toString() {
        if (postedOut) {
            String sign = structure.posted().map(Pounds::toString).orElse("no posting");
            return structure.ident() + " posted at " + sign + ", load refused";
        }
        return structure.ident() + " rates " + factor + "% for " + gross + ", " + verdict;
    }
}
