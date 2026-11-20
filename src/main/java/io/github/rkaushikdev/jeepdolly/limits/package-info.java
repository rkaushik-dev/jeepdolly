/**
 * The published limits: statutory tables, the bridge formula, the envelope.
 *
 * <p>The statutory tables say what a truck may carry with no permit at all, the bridge formula says
 * what any run of axles may carry over the distance it covers, and the size tables say which of the
 * three boxes a load falls in. {@link io.github.rkaushikdev.jeepdolly.limits.WeightReading} reads a
 * stream against every weight rule at once, and {@link
 * io.github.rkaushikdev.jeepdolly.limits.Classification} walks the ladder from no permit to
 * superload.
 */
package io.github.rkaushikdev.jeepdolly.limits;
