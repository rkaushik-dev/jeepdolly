package io.github.rkaushikdev.jeepdolly.limits;

import io.github.rkaushikdev.jeepdolly.domain.Commodity;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import java.util.ArrayList;
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
 * <p>Two things send a load to a study. Its size past the routine box is the usual one. The other
 * is a group whose axles do not share the load: the group limits and the bridge formula both
 * assume they do, so a permit load on a group that is not equalising is a load nobody has weighed
 * properly, and an engineer reads it. A legal load is not this office's business however its axles
 * are loaded, so the test only runs on a load that needs a permit at all.
 *
 * @param reviewReasons the measures past the routine box, then each group that is not equalised, as
 *     {@code unequal tridem}
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
            return refused(size, overweight, commodity.code() + " could have been loaded to legal weight");
        }
        List<String> crossed = thresholds.crossedBy(envelope, reading);
        if (!crossed.isEmpty()) {
            return superload(size, overweight, crossed);
        }
        List<String> review = reasonsForReview(envelope, reading, sizeTable);
        if (!review.isEmpty()) {
            return study(size, overweight, review);
        }
        return new Classification(PermitKind.ORDINARY, size, overweight);
    }

    /** A superload, and the thresholds it is past. */
    public static Classification superload(SizeClass size, boolean overweight, List<String> crossed) {
        return new Classification(PermitKind.SUPERLOAD, size, overweight, crossed, List.of(), Optional.empty());
    }

    /** A study, and why an engineer reads it. */
    public static Classification study(SizeClass size, boolean overweight, List<String> review) {
        return new Classification(PermitKind.STUDY, size, overweight, List.of(), review, Optional.empty());
    }

    /** A load no permit can be written for, and the sentence that says why. */
    public static Classification refused(SizeClass size, boolean overweight, String why) {
        return new Classification(PermitKind.NONE, size, overweight, List.of(), List.of(), Optional.of(why));
    }

    /** Why an engineer reads this load: the measures past the routine box, then each unequal group. */
    public static List<String> reasonsForReview(Envelope envelope, WeightReading reading, SizeTable sizeTable) {
        List<String> found = new ArrayList<>(sizeTable.reasonsForReview(envelope));
        reading.unequalGroups()
                .forEach(one -> found.add("unequal " + one.group().name()));
        return List.copyOf(found);
    }

    /** Whether a permit can be written: nothing refused, and a rung above none. */
    public boolean permittable() {
        return refusal.isEmpty() && kind != PermitKind.NONE;
    }

    /** As the counter says it: {@code study on height, unequal tridem}, {@code no permit needed}. */
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
