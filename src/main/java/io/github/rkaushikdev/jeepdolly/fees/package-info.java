/**
 * The fee schedule, and pricing a permit off it.
 *
 * <p>Every number in a schedule is one the legislature or the commissioner can change without
 * anybody touching the system, which is why they are all on a record with an effective date rather
 * than written into the pricing. A quote is a list of lines and a total, never a single number,
 * because the first thing a hauler does with a fee is ask what it is for.
 */
package io.github.rkaushikdev.jeepdolly.fees;
