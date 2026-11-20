package io.github.rkaushikdev.jeepdolly.permits;

/**
 * One thing that moved between a permit and the revised application that amends it.
 *
 * @param what the kind of change, as the letter names it: {@code dates}, {@code trip}, {@code
 *     gross}, {@code size} or {@code route}
 * @param before how the permit reads now
 * @param after how the replacement will read
 */
public record Change(String what, String before, String after) {

    /** As the letter prints it: {@code dates: 2025-04-08 to 2025-04-10 to 2025-04-08 to 2025-04-14}. */
    @Override
    public String toString() {
        return what + ": " + before + " to " + after;
    }
}
