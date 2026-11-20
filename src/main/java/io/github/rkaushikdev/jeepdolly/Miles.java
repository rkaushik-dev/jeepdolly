package io.github.rkaushikdev.jeepdolly;

import java.util.Locale;

/**
 * A distance along the network, in hundredths of a mile.
 *
 * <p>Hundredths, because the state road log is published in hundredths and a permit is priced per
 * mile off that log.
 */
public record Miles(int hundredths) implements Comparable<Miles> {

    public static final int HUNDREDTHS_PER_MILE = 100;

    /** No distance at all. */
    public static final Miles ZERO = new Miles(0);

    /** Whole miles. */
    public static Miles of(int miles) {
        return of(miles, 0);
    }

    /** Miles and hundredths. */
    public static Miles of(int miles, int hundredths) {
        return new Miles(miles * HUNDREDTHS_PER_MILE + hundredths);
    }

    /**
     * Read {@code 12.40} or {@code 12} off the road log. Two places, never more.
     *
     * @throws Unreadable when the text is not a distance the road log could have printed
     */
    public static Miles parse(String text) {
        String cleaned = removeSuffix(removeSuffix(text.strip().toLowerCase(Locale.ROOT), "miles"), "mi")
                .strip();
        int point = cleaned.indexOf('.');
        if (point < 0) {
            if (!Written.isDigits(cleaned)) {
                throw unreadable(text);
            }
            return of(Integer.parseInt(cleaned));
        }
        String whole = cleaned.substring(0, point);
        String part = cleaned.substring(point + 1);
        if (!Written.isDigits(whole) || !Written.isDigits(part) || part.length() > 2) {
            throw unreadable(text);
        }
        String padded = part.length() == 1 ? part + "0" : part;
        return of(Integer.parseInt(whole), Integer.parseInt(padded));
    }

    private static String removeSuffix(String text, String suffix) {
        return text.endsWith(suffix) ? text.substring(0, text.length() - suffix.length()) : text;
    }

    private static Unreadable unreadable(String text) {
        return new Unreadable("cannot read " + Written.quoted(text) + " as a distance", Facts.of("written", text));
    }

    /** Whole miles, rounded up. A permit is priced on the mile above. */
    public int roundedUp() {
        int whole = Math.floorDiv(hundredths, HUNDREDTHS_PER_MILE);
        int part = Math.floorMod(hundredths, HUNDREDTHS_PER_MILE);
        return whole + (part != 0 ? 1 : 0);
    }

    public Miles plus(Miles other) {
        return new Miles(hundredths + other.hundredths);
    }

    public Miles minus(Miles other) {
        return new Miles(hundredths - other.hundredths);
    }

    @Override
    public int compareTo(Miles other) {
        return Integer.compare(hundredths, other.hundredths);
    }

    /** Two places, the way the road log prints a distance: {@code 12.40 mi}. */
    @Override
    public String toString() {
        int whole = Math.floorDiv(hundredths, HUNDREDTHS_PER_MILE);
        int part = Math.floorMod(hundredths, HUNDREDTHS_PER_MILE);
        return String.format(Locale.ROOT, "%d.%02d mi", whole, part);
    }

    /** The distance as it would be written in code, for a debugging line. */
    public String repr() {
        return "Miles(" + hundredths + ")";
    }
}
