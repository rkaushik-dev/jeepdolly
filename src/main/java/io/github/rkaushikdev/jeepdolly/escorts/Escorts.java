package io.github.rkaushikdev.jeepdolly.escorts;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What a trip has to have with it: pilot cars front and rear, a height pole, a trooper, and for the
 * biggest loads a route survey before any of them sets off.
 *
 * <p>A survey is not an escort. It is somebody driving the route ahead of the move, a week ahead
 * rather than a quarter mile, with a pole set to the load's height, writing down every wire, sign
 * bridge and tree limb the clearance records do not know about. It goes on the escorts because the
 * same pilot companies do it and the same table decides when it is wanted.
 *
 * @param survey the route is to be surveyed and the survey filed before the load moves
 * @param reasons why each escort is wanted, in the order the table read them
 */
public record Escorts(int front, int rear, boolean police, boolean heightPole, boolean survey, List<String> reasons) {

    public Escorts {
        reasons = List.copyOf(reasons);
    }

    /** Escorts with no survey. */
    public Escorts(int front, int rear, boolean police, boolean heightPole, List<String> reasons) {
        this(front, rear, police, heightPole, false, reasons);
    }

    /** Escorts with no survey and no reasons written down beside them. */
    public Escorts(int front, int rear, boolean police, boolean heightPole) {
        this(front, rear, police, heightPole, false, List.of());
    }

    /** How many pilot cars, front and rear together. A trooper is not a pilot car. */
    public int total() {
        return front + rear;
    }

    /** Whether the trip needs anybody with it at all. */
    public boolean any() {
        return total() != 0 || police;
    }

    /** A count of pilot cars as the permit says it: {@code 1 certified pilot car}, {@code 2 certified pilot cars}. */
    public static String cars(int count) {
        return count + " certified pilot car" + (count == 1 ? "" : "s");
    }

    /** The lines that go on the permit face, in the order they are printed. */
    public List<String> conditions() {
        return parts().stream().map(Map.Entry::getKey).toList();
    }

    /** As the counter says it: {@code 1 front with height pole, 1 rear, police escort, route survey}. */
    @Override
    public String toString() {
        List<Map.Entry<String, String>> parts = parts();
        if (parts.isEmpty()) {
            return "no escorts";
        }
        return String.join(", ", parts.stream().map(Map.Entry::getValue).toList());
    }

    /** Each thing the trip has with it, as the permit prints it and as the counter says it. */
    private List<Map.Entry<String, String>> parts() {
        List<Map.Entry<String, String>> found = new ArrayList<>();
        if (front != 0) {
            String pole = heightPole ? " carrying a height pole set one inch above the load" : "";
            String spoken = heightPole ? " with height pole" : "";
            found.add(Map.entry(cars(front) + " ahead of the load" + pole, front + " front" + spoken));
        }
        if (rear != 0) {
            found.add(Map.entry(cars(rear) + " behind the load", rear + " rear"));
        }
        if (police) {
            found.add(Map.entry("police escort, arranged by the carrier with the state police", "police escort"));
        }
        if (survey) {
            found.add(Map.entry(
                    "route surveyed with a height pole and the survey filed before the load moves", "route survey"));
        }
        return found;
    }
}
