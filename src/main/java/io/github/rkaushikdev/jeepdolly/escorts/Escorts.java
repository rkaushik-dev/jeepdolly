package io.github.rkaushikdev.jeepdolly.escorts;

import java.util.ArrayList;
import java.util.List;

/**
 * What a trip has to have with it: pilot cars front and rear, a height pole, a trooper.
 *
 * @param reasons why each escort is wanted, in the order the table read them
 */
public record Escorts(int front, int rear, boolean police, boolean heightPole, List<String> reasons) {

    public Escorts {
        reasons = List.copyOf(reasons);
    }

    /** Escorts with no reasons written down beside them. */
    public Escorts(int front, int rear, boolean police, boolean heightPole) {
        this(front, rear, police, heightPole, List.of());
    }

    /** How many pilot cars, front and rear together. A trooper is not a pilot car. */
    public int total() {
        return front + rear;
    }

    /** Whether the trip needs anybody with it at all. */
    public boolean any() {
        return total() != 0 || police;
    }

    /** The lines that go on the permit face, in the order they are printed. */
    public List<String> conditions() {
        if (!any()) {
            return List.of();
        }
        List<String> found = new ArrayList<>();
        if (front != 0) {
            String pole = heightPole ? " carrying a height pole set one inch above the load" : "";
            found.add(front + " certified pilot car ahead of the load" + pole);
        }
        if (rear != 0) {
            found.add(rear + " certified pilot car behind the load");
        }
        if (police) {
            found.add("police escort, arranged by the carrier with the state police");
        }
        return List.copyOf(found);
    }

    /** As the counter says it: {@code 1 front with height pole, 1 rear, police escort}. */
    @Override
    public String toString() {
        if (!any()) {
            return "no escorts";
        }
        List<String> parts = new ArrayList<>();
        if (front != 0) {
            parts.add(front + " front" + (heightPole ? " with height pole" : ""));
        }
        if (rear != 0) {
            parts.add(rear + " rear");
        }
        if (police) {
            parts.add("police escort");
        }
        return String.join(", ", parts);
    }
}
