package io.github.rkaushikdev.jeepdolly.routing;

/**
 * One corner a load gets round, but not by much, which is a condition and a man in the road with a
 * flag.
 *
 * @param wanted the radius the load wants, in whole feet
 */
public record CloseRun(Corner corner, int wanted) {

    /** How many feet of radius the corner has over what the load wants. */
    public int spare() {
        return corner.radiusFeet() - wanted;
    }

    @Override
    public String toString() {
        return corner.node() + ": the corner is " + corner.radiusFeet() + " ft and this load wants " + wanted
                + " ft, so it clears by " + spare() + " ft";
    }
}
