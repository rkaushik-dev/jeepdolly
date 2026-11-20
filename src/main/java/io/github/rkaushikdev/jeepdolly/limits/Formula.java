package io.github.rkaushikdev.jeepdolly.limits;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.domain.AxleGroup;
import io.github.rkaushikdev.jeepdolly.domain.AxleStream;
import io.github.rkaushikdev.jeepdolly.domain.Span;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The bridge formula, which is the rule that catches what the tables miss.
 *
 * <p>The statutory tables limit what one group may carry. The formula limits what any run of axles
 * may carry over the distance it covers, and it is the reason a legal tandem can still be refused:
 * put two legal groups close enough together and the pair of them is over.
 *
 * <pre>W = 500 * ( L * N / (N - 1) + 12 * N + 36 )</pre>
 *
 * <p>{@code N} is the number of axles in the run and {@code L} is the distance in feet between the
 * first and the last of them. The published table rounds {@code L} to the nearest foot and the
 * answer to the nearest five hundred pounds, and this does the same, because a permit that disagrees
 * with the wall chart in the weigh station is a permit somebody has to defend at the roadside. The
 * answer is capped at the gross limit for the road.
 *
 * <p>There is one exception in the federal rule and it is the reason ordinary five axle semis are
 * legal at all. Two consecutive sets of tandems may carry thirty four thousand pounds each when the
 * outer axles are thirty six feet apart or more, which the formula on its own would refuse. The
 * exception is written into the check rather than into the arithmetic, so a report can say which
 * rule let a load through.
 */
public final class Formula {

    /** The distance the two tandem exception needs between the outer axles. */
    public static final Inches TANDEM_EXCEPTION_REACH = Inches.of(36);

    /** What each of those two tandems may carry under the exception. */
    public static final Pounds TANDEM_EXCEPTION_WEIGHT = new Pounds(34000);

    /** The published table rounds its answers to this many pounds. */
    public static final int ROUNDING = 500;

    /** The name a finding carries when the exception let it through. */
    public static final String TWO_TANDEM_EXCEPTION = "two tandem exception";

    private Formula() {}

    /**
     * The distance in whole feet, rounded to the nearest one.
     *
     * @throws Impossible for a negative distance
     */
    public static int feetOf(Inches extent) {
        if (extent.value() < 0) {
            throw new Impossible("a run of axles does not cover a negative distance");
        }
        return Math.floorDiv(extent.value() + 6, 12);
    }

    /** The published table rounds to the nearest five hundred. */
    public static Pounds roundTo500(int pounds) {
        return new Pounds(Math.floorDiv(pounds + ROUNDING / 2, ROUNDING) * ROUNDING);
    }

    /**
     * What a run of {@code count} axles over {@code extent} may carry, capped at {@code cap}.
     *
     * @throws Impossible for fewer than two axles, which the formula says nothing about
     */
    public static Pounds limit(int count, Inches extent, Pounds cap) {
        if (count < 2) {
            throw new Impossible("the formula speaks about two axles or more");
        }
        int length = feetOf(extent);
        int raw = 500 * (length * count + (12 * count + 36) * (count - 1));
        Pounds allowed = roundTo500(Math.floorDiv(raw, count - 1));
        return lesser(allowed, cap);
    }

    /**
     * Whether the federal two tandem exception reaches this run.
     *
     * <p>Four axles, standing as two tandems, thirty six feet or more apart, and neither tandem over
     * thirty four thousand pounds.
     */
    public static boolean twoTandemException(AxleStream stream, Span span) {
        if (span.count() != 4) {
            return false;
        }
        if (span.extent().compareTo(TANDEM_EXCEPTION_REACH) < 0) {
            return false;
        }
        int first = span.first();
        int last = span.last();
        if (stream.distance(first, first + 1).compareTo(AxleGroup.REACH) > 0) {
            return false;
        }
        if (stream.distance(last - 1, last).compareTo(AxleGroup.REACH) > 0) {
            return false;
        }
        Pounds front = span.axles().get(0).weight().plus(span.axles().get(1).weight());
        Pounds back = span.axles().get(2).weight().plus(span.axles().get(3).weight());
        return front.compareTo(TANDEM_EXCEPTION_WEIGHT) <= 0 && back.compareTo(TANDEM_EXCEPTION_WEIGHT) <= 0;
    }

    /** Every run of the stream against the formula, in span order. */
    public static List<Finding> measure(AxleStream stream, Pounds cap) {
        List<Finding> found = new ArrayList<>();
        for (Span span : stream.spans()) {
            Pounds allowed = limit(span.count(), span.extent(), cap);
            Optional<String> exception = Optional.empty();
            if (span.weight().compareTo(allowed) > 0 && twoTandemException(stream, span)) {
                allowed = TANDEM_EXCEPTION_WEIGHT.times(2);
                exception = Optional.of(TWO_TANDEM_EXCEPTION);
            }
            found.add(new Finding(span, lesser(allowed, cap), exception));
        }
        return List.copyOf(found);
    }

    /** Only the runs that are over, which is what a refusal letter lists. */
    public static List<Finding> failures(AxleStream stream, Pounds cap) {
        return measure(stream, cap).stream()
                .filter(finding -> !finding.passes())
                .toList();
    }

    /** The run furthest over, the first of them on a tie, which is what the counter reads out first. */
    public static Optional<Finding> worst(AxleStream stream, Pounds cap) {
        return failures(stream, cap).stream()
                .max(Comparator.comparingInt(finding -> finding.over().value()));
    }

    private static Pounds lesser(Pounds one, Pounds other) {
        return other.compareTo(one) < 0 ? other : one;
    }
}
