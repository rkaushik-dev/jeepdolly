package io.github.rkaushikdev.jeepdolly.domain;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.Pounds;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Every axle under a combination, in road order, with the gap between each one and the next.
 *
 * <p>This is the shape everything downstream reasons about. An application describes a tractor, a
 * jeep dolly, a lowboy and a booster, and the office does not care about any of those words. It
 * cares that eleven axles cross a given point in a given order at given spacings carrying given
 * weights.
 *
 * <p>Two readings come off a stream and they answer different questions. {@link #groups()} answers
 * the statutory question: axles within eight feet of each other are one group, and the legislature
 * writes a limit for each. {@link #spans(int)} answers the bridge question: the formula asks about
 * every run of consecutive axles, from the first two to the whole train, because the run that fails
 * is rarely the one a person would have picked.
 *
 * <p>Two streams are the same stream when the axles and gaps match, which the round trip through
 * the database and the amendment check both rely on.
 *
 * @param spacings one fewer entry than {@code axles}. A one axle stream is allowed to exist so that
 *     nothing downstream has to special case it, but nothing will let a permit be written for one.
 */
public record AxleStream(List<Axle> axles, List<Inches> spacings) {

    public AxleStream {
        axles = List.copyOf(axles);
        spacings = List.copyOf(spacings);
        if (axles.isEmpty()) {
            throw new Impossible("a stream holds at least one axle");
        }
        if (spacings.size() != axles.size() - 1) {
            throw new Impossible(
                    axles.size() + " axles need " + (axles.size() - 1) + " spacings, got " + spacings.size());
        }
        for (Inches spacing : spacings) {
            if (spacing.value() <= 0) {
                throw new Impossible("two axles cannot stand in the same place");
            }
        }
    }

    public int count() {
        return axles.size();
    }

    /** Everything on the road, which is the number on the scale ticket. */
    public Pounds gross() {
        Pounds total = Pounds.ZERO;
        for (Axle axle : axles) {
            total = total.plus(axle.weight());
        }
        return total;
    }

    /** First axle to last axle. */
    public Inches extent() {
        return distance(0, count() - 1);
    }

    /**
     * Centre to centre between two axles by index.
     *
     * @throws IndexOutOfBoundsException when either axle is not in the stream, or they are reversed
     */
    public Inches distance(int first, int last) {
        if (first < 0 || first > last || last >= count()) {
            throw new IndexOutOfBoundsException("no axles " + first + " to " + last + " in a stream of " + count());
        }
        Inches total = Inches.ZERO;
        for (Inches spacing : spacings.subList(first, last)) {
            total = total.plus(spacing);
        }
        return total;
    }

    /** Where each axle sits, measured from the first one. */
    public List<Inches> offsets() {
        Inches running = Inches.ZERO;
        List<Inches> found = new ArrayList<>();
        found.add(running);
        for (Inches spacing : spacings) {
            running = running.plus(spacing);
            found.add(running);
        }
        return List.copyOf(found);
    }

    /** Every run of two or more consecutive axles. */
    public List<Span> spans() {
        return spans(2);
    }

    /**
     * Every run of at least {@code minimum} consecutive axles, shortest first, then left to right.
     *
     * <p>The order matters to the reports rather than to the arithmetic: a clerk reading a refusal
     * wants the tightest failing run named first.
     *
     * @throws Impossible for runs of no axles
     */
    public List<Span> spans(int minimum) {
        if (minimum < 1) {
            throw new Impossible("a span holds at least one axle");
        }
        List<Span> found = new ArrayList<>();
        for (int length = minimum; length <= count(); length++) {
            for (int first = 0; first <= count() - length; first++) {
                int last = first + length - 1;
                found.add(new Span(first, last, axles.subList(first, last + 1), distance(first, last)));
            }
        }
        return List.copyOf(found);
    }

    /** Split at every gap wider than the group reach. */
    public List<AxleGroup> groups() {
        List<AxleGroup> found = new ArrayList<>();
        int start = 0;
        for (int index = 0; index < spacings.size(); index++) {
            if (spacings.get(index).compareTo(AxleGroup.REACH) > 0) {
                found.add(group(start, index));
                start = index + 1;
            }
        }
        found.add(group(start, count() - 1));
        return List.copyOf(found);
    }

    private AxleGroup group(int first, int last) {
        return new AxleGroup(axles.subList(first, last + 1), spacings.subList(first, last));
    }

    /** The heaviest axle, the first of them when two weigh the same. */
    public Axle heaviestAxle() {
        return Collections.max(axles, Comparator.comparing(Axle::weight));
    }

    public int liftableCount() {
        return (int) axles.stream().filter(Axle::liftable).count();
    }

    public int size() {
        return axles.size();
    }

    /** For the worksheet: {@code AxleStream(6 axles, 94,000 lb, 44 ft 10 in)}. */
    @Override
    public String toString() {
        return "AxleStream(" + count() + " axles, " + gross() + ", " + extent() + ")";
    }
}
