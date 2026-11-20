package io.github.rkaushikdev.jeepdolly.permits;

/**
 * Anything that will hand out the next sequence for a series and a year, one at a time.
 *
 * <p>The in memory {@link Counter} and the locked counter row in the database both satisfy this,
 * which is what lets the office be assembled the same way for a test and for a live request.
 * Nothing that implements it is allowed to reserve a block and hand back what it did not use:
 * accounting reconciles the money against the numbers issued, so a missing number is an hour of
 * somebody's afternoon.
 */
public interface Sequences {

    /**
     * The next sequence for the year and series, which is then used up.
     *
     * @throws io.github.rkaushikdev.jeepdolly.Conflict when the series is full for the year
     */
    int take(int year, Series series);

    /** How many sequences have been handed out for the year and series. */
    int issued(int year, Series series);
}
