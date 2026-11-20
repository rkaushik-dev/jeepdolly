package io.github.rkaushikdev.jeepdolly.limits;

import io.github.rkaushikdev.jeepdolly.domain.Commodity;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import java.util.List;
import java.util.Optional;

/**
 * What kind of permit a load is, and every reason it did not stop lower down the ladder.
 *
 * <p>An application walks up the ladder until it stops: nothing, ordinary, study, superload. Two
 * things sit outside the ladder and beat it. A divisible load gets nothing, whatever its numbers,
 * unless its commodity carries an allowance; that test runs first because a load that should have
 * been two trips does not deserve an engineer's afternoon. A trip that only wants a town way gets
 * nothing either, but that is checked on the route rather than here.
 *
 * @param refusal why no permit can be written at all, when that is the answer
 */
public record Classification(
        PermitKind kind,
        SizeClass size,
        boolean overweight,
        List<String> superloadReasons,
        List<String> reviewReasons,
        Optional<String> refusal) {

    public Classification {
        superloadReasons = List.copyOf(superloadReasons);
        reviewReasons = List.copyOf(reviewReasons);
    }

    /** A rung reached with no reasons to give and nothing refused. */
    public Classification(PermitKind kind, SizeClass size, boolean overweight) {
        this(kind, size, overweight, List.of(), List.of(), Optional.empty());
    }

    /** Walk the ladder once against the published thresholds. */
    public static Classification classify(
            Envelope envelope, WeightReading reading, SizeTable sizeTable, Commodity commodity) {
        return classify(envelope, reading, sizeTable, commodity, Thresholds.STANDARD);
    }

    /** Walk the ladder once and report where it stopped and why. */
    public static Classification classify(
            Envelope envelope, WeightReading reading, SizeTable sizeTable, Commodity commodity, Thresholds thresholds) {
        SizeClass size = sizeTable.classify(envelope);
        boolean overweight = reading.overweight();
        if (size == SizeClass.LEGAL && !overweight) {
            return new Classification(PermitKind.NONE, size, overweight);
        }
        if (!commodity.permittable()) {
            return new Classification(
                    PermitKind.NONE,
                    size,
                    overweight,
                    List.of(),
                    List.of(),
                    Optional.of(commodity.code() + " could have been loaded to legal weight"));
        }
        List<String> crossed = thresholds.crossedBy(envelope, reading);
        if (!crossed.isEmpty()) {
            return new Classification(PermitKind.SUPERLOAD, size, overweight, crossed, List.of(), Optional.empty());
        }
        List<String> review = sizeTable.reasonsForReview(envelope);
        if (!review.isEmpty()) {
            return new Classification(PermitKind.STUDY, size, overweight, List.of(), review, Optional.empty());
        }
        return new Classification(PermitKind.ORDINARY, size, overweight);
    }

    /** Whether a permit can be written: nothing refused, and a rung above none. */
    public boolean permittable() {
        return refusal.isEmpty() && kind != PermitKind.NONE;
    }

    /** As the counter says it: {@code study on height}, {@code no permit needed}. */
    @Override
    public String toString() {
        Optional<String> refused = refusal.filter(one -> !one.isEmpty());
        if (refused.isPresent()) {
            return "no permit: " + refused.get();
        }
        if (kind == PermitKind.NONE) {
            return "no permit needed";
        }
        List<String> reasons = superloadReasons.isEmpty() ? reviewReasons : superloadReasons;
        if (!reasons.isEmpty()) {
            return kind + " on " + String.join(", ", reasons);
        }
        return kind.toString();
    }
}
