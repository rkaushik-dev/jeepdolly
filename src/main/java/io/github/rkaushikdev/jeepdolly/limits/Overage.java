package io.github.rkaushikdev.jeepdolly.limits;

import io.github.rkaushikdev.jeepdolly.Pounds;

/**
 * One rule's finding about one part of a stream: how far over it is, and the line the letter prints.
 *
 * <p>Four rules speak to weight and each finds in its own shape: the gross against the road, a group
 * against the table, a run of axles against the formula, an axle against its tires. They are read
 * the same way afterwards. A finding over by nothing passes, the worst one sets the fee, and every
 * one that does not pass is a line of the letter, in the words its own rule uses.
 */
public sealed interface Overage permits GrossFinding, GroupFinding, Finding, TireFinding {

    /** How far past what is allowed, never below zero. */
    Pounds over();

    /** Whether the rule finds nothing over. */
    default boolean passes() {
        return over().value() == 0;
    }
}
