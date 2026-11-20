package io.github.rkaushikdev.jeepdolly.routing;

import io.github.rkaushikdev.jeepdolly.Conflict;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The card index of corners, keyed the way a route is walked.
 *
 * <p>The office knows perhaps two hundred corners that stop long loads and has kept them on a card
 * index for thirty years. The index is a record rather than a rule: it says what the corners are,
 * and {@link Sweep} says what a combination needs to get round one.
 */
public final class TurnBook {

    /**
     * The radius under which a corner goes on the list on the office wall.
     *
     * <p>This is not the same number as the plain radius and it is deliberately above it. A forty
     * two foot corner gets an ordinary lowboy round and stops everything longer, which is exactly the
     * kind of corner the counter wants to know about before it quotes a route rather than after.
     */
    public static final int TIGHT_FEET = 50;

    /**
     * The corners the office has on the card index, as they stand in the code.
     *
     * <p>These are here for the same reason the statutory tables have a coded copy: a database that
     * has not been seeded still has to answer the telephone, and an office with no corners on file
     * would wave a hundred and forty feet of bridge beam through Norridgewock village.
     */
    public static final List<Corner> CORNERS = List.of(
            new Corner("norridgewock", "US2-1", "SR8-1", 42, "ninety degrees at the square, granite kerb"),
            new Corner("norridgewock", "US2-1", "SA148-1", 70, "wide but the bank is soft on the outside"),
            new Corner("skowhegan", "US201-2", "US2-1", 65, "island in the middle, keep it on the left"),
            new Corner("skowhegan", "US201-2", "SA148-2", 62, "off the bridge and hard right, watch the kerb"),
            new Corner("skowhegan", "US201-2", "US201-3", 80, ""),
            new Corner("canaan", "SA148-1", "SR43-1", 70, ""),
            new Corner("canaan", "SA148-2", "SR43-1", 75, ""),
            new Corner("madison", "SR8-1", "TR104-1", 35, "mill gate, and the gate posts are the limit"),
            new Corner("pittsfield", "SR43-1", "US2-2", 68, ""),
            new Corner("fairfield", "I95-1", "I95-2", 120, "interstate ramp, nothing gets stuck here"));

    private final List<Corner> corners = new ArrayList<>();

    /** The card index as it stands in the code, for an office with no rows. */
    public static TurnBook coded() {
        TurnBook book = new TurnBook();
        CORNERS.forEach(book::add);
        return book;
    }

    /**
     * Put a corner on the index.
     *
     * @throws Conflict when the same corner is already on file, either way round
     */
    public Corner add(Corner corner) {
        for (Corner existing : corners) {
            if (existing.node().equals(corner.node()) && existing.joins(corner.into(), corner.outOf())) {
                throw new Conflict("the corner at " + corner.node() + " is already on file");
            }
        }
        corners.add(corner);
        return corner;
    }

    /** The corner at a node between two segments, whichever way they are driven, if one is on file. */
    public Optional<Corner> between(String node, String first, String second) {
        return corners.stream()
                .filter(one -> one.node().equals(node) && one.joins(first, second))
                .findFirst();
    }

    /** Every corner on file at one node, in the order they were filed. */
    public List<Corner> at(String node) {
        return corners.stream().filter(one -> one.node().equals(node)).toList();
    }

    /** The corners under the wall list's radius, tightest first. */
    public List<Corner> tight() {
        return tight(TIGHT_FEET);
    }

    /** The corners under {@code feet} of radius, tightest first. */
    public List<Corner> tight(int feet) {
        return corners.stream()
                .filter(one -> one.radiusFeet() < feet)
                .sorted(Comparator.comparingInt(Corner::radiusFeet))
                .toList();
    }

    public int size() {
        return corners.size();
    }
}
