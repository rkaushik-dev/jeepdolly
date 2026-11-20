package io.github.rkaushikdev.jeepdolly.routing;

/**
 * One corner a load will not get round, which is a refusal.
 *
 * @param wanted the radius the load wants, in whole feet
 */
public record TooTight(Corner corner, int wanted) {

    /** How many feet of radius the corner is short of what the load wants. */
    public int shortBy() {
        return wanted - corner.radiusFeet();
    }

    @Override
    public String toString() {
        return corner.node() + ": the corner is " + corner.radiusFeet() + " ft and this load wants " + wanted + " ft";
    }
}
