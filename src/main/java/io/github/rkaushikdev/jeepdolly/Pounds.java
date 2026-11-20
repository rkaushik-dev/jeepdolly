package io.github.rkaushikdev.jeepdolly;

import java.util.List;
import java.util.Locale;

/**
 * A weight, on an axle or a group or the whole combination, in whole pounds.
 *
 * <p>A permit reads 132,400 lb. Tons happen in conversation and never in storage: a weight written
 * in tons is read once and held as pounds from then on.
 */
public record Pounds(int value) implements Comparable<Pounds> {

    public static final int PER_TON = 2000;

    /** Nothing on the scale. */
    public static final Pounds ZERO = new Pounds(0);

    private static final List<String> POUND_SUFFIXES = List.of("pounds", "lbs.", "lbs", "lb.", "lb", "#");
    private static final List<String> TON_SUFFIXES = List.of("tons", "ton");

    public static Pounds ofTons(int tons) {
        return new Pounds(tons * PER_TON);
    }

    /**
     * Read a weight off an application, commas and unit and all: {@code 132,400 lb}, {@code
     * 132400#}, {@code 40 tons}.
     *
     * @throws Unreadable when the text is not a whole number of pounds or tons
     */
    public static Pounds parse(String text) {
        String cleaned = text.strip().toLowerCase(Locale.ROOT).replace(",", "");
        boolean pounds = false;
        for (String suffix : POUND_SUFFIXES) {
            if (cleaned.endsWith(suffix)) {
                cleaned =
                        cleaned.substring(0, cleaned.length() - suffix.length()).strip();
                pounds = true;
                break;
            }
        }
        if (!pounds) {
            for (String suffix : TON_SUFFIXES) {
                if (cleaned.endsWith(suffix)) {
                    String tons = cleaned.substring(0, cleaned.length() - suffix.length())
                            .strip();
                    if (!Written.isDigits(tons)) {
                        throw unreadable(text);
                    }
                    return ofTons(Integer.parseInt(tons));
                }
            }
        }
        if (!Written.isDigits(cleaned)) {
            throw unreadable(text);
        }
        return new Pounds(Integer.parseInt(cleaned));
    }

    private static Unreadable unreadable(String text) {
        return new Unreadable("cannot read " + Written.quoted(text) + " as a weight", Facts.of("written", text));
    }

    /** How far past {@code limit} this runs, never below zero. */
    public Pounds over(Pounds limit) {
        return new Pounds(Math.max(0, value - limit.value));
    }

    public Pounds plus(Pounds other) {
        return new Pounds(value + other.value);
    }

    public Pounds minus(Pounds other) {
        return new Pounds(value - other.value);
    }

    public Pounds times(int count) {
        return new Pounds(value * count);
    }

    public Pounds negated() {
        return new Pounds(-value);
    }

    @Override
    public int compareTo(Pounds other) {
        return Integer.compare(value, other.value);
    }

    /** With the commas the office writes: {@code 132,400 lb}. */
    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%,d lb", value);
    }

    /** The weight as it would be written in code, for a debugging line. */
    public String repr() {
        return "Pounds(" + value + ")";
    }
}
