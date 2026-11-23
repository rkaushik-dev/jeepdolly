package io.github.rkaushikdev.jeepdolly.domain;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.Pounds;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Axles close enough together that the road reads them as one.
 *
 * <p>Axles standing close together are read as one group, because to the pavement under them they
 * are one, and the legislature writes a limit for each size of group.
 *
 * @param spacings the gap between each axle and the next, so a group of three axles carries two
 *     spacings and a single axle carries none
 */
public record AxleGroup(List<Axle> axles, List<Inches> spacings) {

    /** Axles standing this close or closer are read as one group. */
    public static final Inches REACH = Inches.of(8);

    private static final Map<Integer, String> NAMES =
            Map.of(1, "single", 2, "tandem", 3, "tridem", 4, "quad", 5, "quint", 6, "six axle group");

    public AxleGroup {
        axles = List.copyOf(axles);
        spacings = List.copyOf(spacings);
        if (axles.isEmpty()) {
            throw new Impossible("a group holds at least one axle");
        }
        if (spacings.size() != axles.size() - 1) {
            throw new Impossible(
                    axles.size() + " axles need " + (axles.size() - 1) + " spacings, got " + spacings.size());
        }
        for (Inches spacing : spacings) {
            if (spacing.value() <= 0) {
                throw new Impossible("axles in a group stand apart");
            }
            if (spacing.compareTo(REACH) > 0) {
                throw new Impossible(spacing + " is too far apart to be one group");
            }
        }
    }

    /** A single axle, which is a group with no spacing at all. */
    public AxleGroup(List<Axle> axles) {
        this(axles, List.of());
    }

    /**
     * The word the office uses for a group of this many axles.
     *
     * @throws Impossible for a group of no axles
     */
    public static String nameFor(int count) {
        if (count < 1) {
            throw new Impossible("a group holds at least one axle");
        }
        return NAMES.getOrDefault(count, count + " axle group");
    }

    public int count() {
        return axles.size();
    }

    public String name() {
        return nameFor(count());
    }

    public Pounds weight() {
        Pounds total = Pounds.ZERO;
        for (Axle axle : axles) {
            total = total.plus(axle.weight());
        }
        return total;
    }

    /** First axle to last axle, which is what the bridge formula wants. */
    public Inches extent() {
        Inches total = Inches.ZERO;
        for (Inches spacing : spacings) {
            total = total.plus(spacing);
        }
        return total;
    }

    public int liftableCount() {
        return (int) axles.stream().filter(Axle::liftable).count();
    }

    /** The heaviest axle, the first of them when two weigh the same. */
    public Axle heaviest() {
        return Collections.max(axles, Comparator.comparing(Axle::weight));
    }

    public int size() {
        return axles.size();
    }

    /** As a worksheet prints it: {@code tridem 48,000 lb over 8 ft 8 in}. */
    @Override
    public String toString() {
        return name() + " " + weight() + " over " + extent();
    }
}
