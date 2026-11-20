package io.github.rkaushikdev.jeepdolly;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A size on the envelope, in whole inches: width, height, length, overhang.
 *
 * <p>A permit reads 14 ft 6 in. It never reads 14.5 ft, and that is not a matter of taste. An inch
 * of height is the difference between a load that clears the Sebasticook overpass and a load that
 * takes the deck off it, so an inch is a thing this system can name and a fraction of one is not.
 * Whatever the hauler writes on the application, feet and inches or a bare count of inches, parses
 * back to the same integer.
 *
 * <p>Sizes are ordered and closed under the arithmetic the office actually does. None of them will
 * add itself to a bare integer: a width plus 6 is a question about what 6 means, and the answer
 * belongs at the call site where somebody knows.
 */
public record Inches(int value) implements Comparable<Inches> {

    public static final int PER_FOOT = 12;

    /** No size at all, which is what an overhang that does not project measures. */
    public static final Inches ZERO = new Inches(0);

    private static final Pattern FEET_INCHES = Pattern.compile(
            "^\\s*(?<feet>\\d+)\\s*(?:'|ft\\.?|feet)\\s*(?:(?<inches>\\d+)\\s*(?:\"|in\\.?|inches)?)?\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DASHED = Pattern.compile("^\\s*(?<feet>\\d+)\\s*-\\s*(?<inches>\\d+)\\s*$");
    private static final Pattern BARE =
            Pattern.compile("^\\s*(?<inches>\\d+)\\s*(?:\"|in\\.?|inches)?\\s*$", Pattern.CASE_INSENSITIVE);

    /** Whole feet. */
    public static Inches of(int feet) {
        return of(feet, 0);
    }

    /** Feet and inches. The inches part may run past eleven. */
    public static Inches of(int feet, int inches) {
        return new Inches(feet * PER_FOOT + inches);
    }

    /**
     * Read what a hauler wrote on the application.
     *
     * <p>Applications arrive by fax as often as by form and the office has never managed to make
     * anybody write one format. {@code 14'6"}, {@code 14 ft 6 in}, {@code 14-6} and {@code 174} all
     * mean the same 174 inches, so every one of them reads back as 174.
     *
     * @throws Unreadable when the text is not a size in any of those shapes
     */
    public static Inches parse(String text) {
        for (Pattern pattern : List.of(FEET_INCHES, DASHED)) {
            Matcher found = pattern.matcher(text);
            if (found.matches()) {
                int feet = Integer.parseInt(found.group("feet"));
                String part = found.group("inches");
                return of(feet, part == null ? 0 : Integer.parseInt(part));
            }
        }
        Matcher found = BARE.matcher(text);
        if (found.matches()) {
            return new Inches(Integer.parseInt(found.group("inches")));
        }
        throw new Unreadable("cannot read " + Written.quoted(text) + " as a size", Facts.of("written", text));
    }

    /** Whole feet, rounded toward zero. */
    public int feet() {
        return value / PER_FOOT;
    }

    /** What is left over the whole feet, carrying the sign. */
    public int spareInches() {
        return value - feet() * PER_FOOT;
    }

    /** How far past {@code limit} this runs, never below zero. */
    public Inches over(Inches limit) {
        return new Inches(Math.max(0, value - limit.value));
    }

    public Inches plus(Inches other) {
        return new Inches(value + other.value);
    }

    public Inches minus(Inches other) {
        return new Inches(value - other.value);
    }

    public Inches times(int count) {
        return new Inches(value * count);
    }

    public Inches negated() {
        return new Inches(-value);
    }

    public Inches abs() {
        return new Inches(Math.abs(value));
    }

    @Override
    public int compareTo(Inches other) {
        return Integer.compare(value, other.value);
    }

    /** Feet and inches, the way the face of a permit prints a size: {@code 14 ft 6 in}. */
    @Override
    public String toString() {
        String sign = value < 0 ? "-" : "";
        int magnitude = Math.abs(value);
        int feet = magnitude / PER_FOOT;
        int spare = magnitude % PER_FOOT;
        if (spare == 0) {
            return sign + feet + " ft";
        }
        if (feet == 0) {
            return sign + spare + " in";
        }
        return sign + feet + " ft " + spare + " in";
    }

    /** The size as it would be written in code, for a debugging line. */
    public String repr() {
        return "Inches(" + value + ")";
    }
}
