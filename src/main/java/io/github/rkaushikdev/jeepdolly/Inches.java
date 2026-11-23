package io.github.rkaushikdev.jeepdolly;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A size on the envelope, in whole inches: width, height, length, overhang.
 *
 * <p>A permit reads 14 ft 6 in. It never reads 14.5 ft, and that is not a matter of taste. An inch
 * of height is the difference between a load that clears the Sebasticook overpass and a load that
 * takes the deck off it, so an inch is a thing this system can name and a fraction of one is not.
 * Whatever the hauler writes on the application, feet and inches, a bare count of inches or a metric
 * size off a Canadian work order, parses back to the same integer.
 *
 * <p>Sizes are ordered and closed under the arithmetic the office actually does. None of them will
 * add itself to a bare integer: a width plus 6 is a question about what 6 means, and the answer
 * belongs at the call site where somebody knows.
 */
public record Inches(int value) implements Comparable<Inches> {

    public static final int PER_FOOT = 12;

    /** No size at all, which is what an overhang that does not project measures. */
    public static final Inches ZERO = new Inches(0);

    /** The fractions a tape measure reads to, which are the only ones the office accepts. */
    private static final Set<Integer> DENOMINATORS = Set.of(2, 4, 8, 16);

    /** A fraction of an inch, set off by a space or a dash: {@code 6 1/2}, {@code 6-1/2}. */
    private static final String FRACTION = "(?:(?:\\s+|\\s*-\\s*)(?<numerator>\\d+)\\s*/\\s*(?<denominator>\\d+))?";

    private static final String INCH_MARK = "\\s*(?:\"|in\\.?|inches)?";

    private static final Pattern FEET_INCHES = Pattern.compile(
            "^\\s*(?<feet>\\d+)\\s*(?:'|ft\\.?|feet)\\s*(?:(?<inches>\\d+)" + FRACTION + INCH_MARK + ")?\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DASHED =
            Pattern.compile("^\\s*(?<feet>\\d+)\\s*-\\s*(?<inches>\\d+)" + FRACTION + "\\s*$");
    private static final Pattern BARE =
            Pattern.compile("^\\s*(?<inches>\\d+)" + FRACTION + INCH_MARK + "\\s*$", Pattern.CASE_INSENSITIVE);

    /** A metric size, to the millimetre at most: {@code 4.25 m}, {@code 425 cm}, {@code 4250 mm}. */
    private static final Pattern METRIC = Pattern.compile(
            "^\\s*(?<amount>\\d+(?:[.,]\\d{1,3})?)\\s*(?<unit>mm|cm|m|metres|meters)\\s*$", Pattern.CASE_INSENSITIVE);

    /** Millimetres in one of each metric unit the office reads. */
    private static final Map<String, Integer> MILLIMETRES =
            Map.of("mm", 1, "cm", 10, "m", 1000, "metres", 1000, "meters", 1000);

    /** Tenths of a millimetre in an inch, which is exact by definition. */
    private static final int TENTHS_PER_INCH = 254;

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
     * <p>A fraction off the tape, {@code 14'6 1/2"} or {@code 174-1/4}, is read up to the next whole
     * inch. Rounding it down would give the hauler the half inch, and the half inch is the one that
     * finds the bottom of the overpass. Only halves, quarters, eighths and sixteenths are a
     * fraction somebody read off a tape; anything else is refused rather than guessed at.
     *
     * <p>A metric size, which is what a load coming down from New Brunswick arrives with, is read
     * the same way: {@code 4.42 m} is 174.02 inches and goes on the permit as 175. Nothing is read
     * as metric without its unit, so {@code 14.5} on its own is still refused, because it is just
     * as likely to be fourteen and a half feet.
     *
     * @throws Unreadable when the text is not a size in any of those shapes
     */
    public static Inches parse(String text) {
        return imperial(text).or(() -> metric(text)).orElseThrow(() -> unreadable(text));
    }

    private static Optional<Inches> imperial(String text) {
        for (Pattern pattern : List.of(FEET_INCHES, DASHED, BARE)) {
            Matcher found = pattern.matcher(text);
            if (found.matches()) {
                int feet = whole(found, "feet");
                int inches = whole(found, "inches");
                return Optional.of(of(feet, inches + fractionRoundedUp(found, text)));
            }
        }
        return Optional.empty();
    }

    /** A metric size converted to inches, rounded up for the same reason a fraction is. */
    private static Optional<Inches> metric(String text) {
        Matcher found = METRIC.matcher(text);
        if (!found.matches()) {
            return Optional.empty();
        }
        BigDecimal amount = new BigDecimal(found.group("amount").replace(',', '.'));
        int unit = MILLIMETRES.get(found.group("unit").toLowerCase(Locale.ROOT));
        BigDecimal tenths = amount.multiply(BigDecimal.valueOf(unit * 10L));
        return Optional.of(new Inches(tenths.divide(BigDecimal.valueOf(TENTHS_PER_INCH), 0, RoundingMode.CEILING)
                .intValueExact()));
    }

    /** A named number off the match, nothing when the shape has no such part or it was left out. */
    private static int whole(Matcher found, String name) {
        if (!found.namedGroups().containsKey(name)) {
            return 0;
        }
        String part = found.group(name);
        return part == null ? 0 : Integer.parseInt(part);
    }

    /** One inch for any fraction written, none for no fraction, a refusal for one nobody reads off a tape. */
    private static int fractionRoundedUp(Matcher found, String text) {
        String numerator = found.group("numerator");
        if (numerator == null) {
            return 0;
        }
        int top = Integer.parseInt(numerator);
        int bottom = Integer.parseInt(found.group("denominator"));
        if (!DENOMINATORS.contains(bottom) || top < 1 || top >= bottom) {
            throw unreadable(text);
        }
        return 1;
    }

    private static Unreadable unreadable(String text) {
        return new Unreadable("cannot read " + Written.quoted(text) + " as a size", Facts.of("written", text));
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
