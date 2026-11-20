package io.github.rkaushikdev.jeepdolly.routing;

import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Miles;
import io.github.rkaushikdev.jeepdolly.domain.HighwayClass;
import io.github.rkaushikdev.jeepdolly.domain.Network;
import io.github.rkaushikdev.jeepdolly.domain.Segment;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

/**
 * A trip as the segments it drives, in the order they are driven, checked to join end to end.
 *
 * <p>A permit names a route and the route is a list of segments. That is worth being strict about,
 * because the two other ways of naming a route both fail. Naming the roads fails: take US 201 to US
 * 2 to SA 148 does not say where the turns are, and a hauler who joins US 2 at the wrong end drives
 * forty miles the wrong way and over a posted bridge. Naming the towns fails for the same reason
 * with fewer words.
 *
 * <p>So an itinerary is segment idents, checked to join end to end, from a stated origin. It knows
 * what it touches: the classes of road, the districts, the total distance, and each class of road
 * separately, because the fee is priced per mile by class and the seasonal orders are read by
 * district.
 *
 * <p>An itinerary is built once and not edited. A detour makes a new one. That matters when a permit
 * is amended, because the old itinerary has to stay exactly as it was for the copy that was already
 * printed and handed over.
 */
public final class Itinerary implements Iterable<Leg> {

    private final String origin;
    private final String destination;
    private final List<Leg> legs;

    /**
     * Walk the idents from the origin, checking each one joins the one before it.
     *
     * @throws Impossible when there are no segments, or one cannot be driven from where the trip
     *     stands
     * @throws io.github.rkaushikdev.jeepdolly.NotOnFile when the origin or a segment is not on the
     *     road log
     */
    public Itinerary(Network network, String origin, List<String> idents) {
        if (idents.isEmpty()) {
            throw new Impossible("an itinerary has at least one segment");
        }
        network.node(origin);
        List<Leg> walked = new ArrayList<>();
        String standing = origin;
        for (String ident : idents) {
            Segment segment = network.segment(ident);
            if (!segment.runsFrom(standing)) {
                throw new Impossible(
                        "segment " + ident + " cannot be driven from " + standing,
                        Facts.of("segment", ident, "at", standing));
            }
            walked.add(new Leg(segment, standing));
            standing = segment.otherEnd(standing);
        }
        this.origin = origin;
        this.destination = standing;
        this.legs = List.copyOf(walked);
    }

    public String origin() {
        return origin;
    }

    /** Where the last leg leaves the trip. */
    public String destination() {
        return destination;
    }

    /** The legs in driven order. */
    public List<Leg> legs() {
        return legs;
    }

    /** The segment idents in driven order, which is what a permit records as its route. */
    public List<String> idents() {
        return legs.stream().map(leg -> leg.segment().ident()).toList();
    }

    /** The segments added up off the road log. */
    public Miles distance() {
        Miles total = Miles.ZERO;
        for (Leg leg : legs) {
            total = total.plus(leg.length());
        }
        return total;
    }

    /** The miles driven on one class of road, because the fee is priced that way. */
    public Miles distanceOn(HighwayClass highwayClass) {
        Miles total = Miles.ZERO;
        for (Leg leg : legs) {
            if (leg.segment().highwayClass() == highwayClass) {
                total = total.plus(leg.length());
            }
        }
        return total;
    }

    /** The classes of road the trip touches, sorted by the name they are stored under. */
    public List<HighwayClass> classes() {
        return legs.stream()
                .map(leg -> leg.segment().highwayClass())
                .distinct()
                .sorted(Comparator.comparing(HighwayClass::value))
                .toList();
    }

    /** The districts the trip touches, in number order, which the seasonal orders are read by. */
    public List<Integer> districts() {
        TreeSet<Integer> found = new TreeSet<>();
        legs.forEach(leg -> found.add(leg.segment().jurisdiction().district()));
        return List.copyOf(found);
    }

    /** The origin and then the node every leg leaves at. */
    public List<String> towns() {
        List<String> found = new ArrayList<>();
        found.add(origin);
        legs.forEach(leg -> found.add(leg.leftAt()));
        return List.copyOf(found);
    }

    public boolean touches(String ident) {
        return idents().contains(ident);
    }

    /** Segments this office cannot write a permit over, in driven order. */
    public List<String> unpermittable() {
        return legs.stream()
                .filter(leg -> !leg.segment().highwayClass().permittable())
                .map(leg -> leg.segment().ident())
                .toList();
    }

    /**
     * The route as it prints on the permit face, as a driver would read it: {@code from Waterville,
     * US 201 to Fairfield, US 2 to Norridgewock}. Two legs in a row that read the same are printed
     * once.
     */
    public String narrative() {
        List<String> parts = new ArrayList<>();
        for (Leg leg : legs) {
            String step = leg.segment().route() + " to " + titled(leg.leftAt());
            if (parts.isEmpty() || !parts.get(parts.size() - 1).equals(step)) {
                parts.add(step);
            }
        }
        return "from " + titled(origin) + ", " + String.join(", ", parts);
    }

    /**
     * A place name capitalised the way a road sign prints it: the first letter of every word up,
     * the rest down.
     */
    private static String titled(String name) {
        StringBuilder out = new StringBuilder(name.length());
        boolean inWord = false;
        for (int index = 0; index < name.length(); index++) {
            char one = name.charAt(index);
            boolean cased = Character.isUpperCase(one) || Character.isLowerCase(one) || Character.isTitleCase(one);
            out.append(cased && !inWord ? Character.toTitleCase(one) : Character.toLowerCase(one));
            inWord = cased;
        }
        return out.toString();
    }

    /** How many legs the trip has. */
    public int size() {
        return legs.size();
    }

    @Override
    public Iterator<Leg> iterator() {
        return legs.iterator();
    }

    /** For the worksheet: {@code Itinerary(waterville to pittsfield, 5 legs)}. */
    @Override
    public String toString() {
        return "Itinerary(" + origin + " to " + destination + ", " + legs.size() + " legs)";
    }
}
