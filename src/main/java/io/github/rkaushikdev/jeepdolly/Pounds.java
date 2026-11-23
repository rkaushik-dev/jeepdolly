package io.github.rkaushikdev.jeepdolly;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A weight, on an axle or a group or the whole combination, in whole pounds.
 *
 * <p>A permit reads 132,400 lb. Tons happen in conversation and never in storage: a weight written
 * in tons is read once and held as pounds from then on. Kilograms and tonnes arrive on the scale
 * tickets of loads coming in over the border, and they are read once the same way.
 */
public record Pounds(int value) implements Comparable<Pounds> {

    public static final int PER_TON = 2000;

    /** Nothing on the scale. */
    public static final Pounds ZERO = new Pounds(0);

    /** Kilograms in a pound, exact by the 1959 agreement, which is what every certified scale uses. */
    public static final BigDecimal KILOGRAMS_PER_POUND = new BigDecimal("0.45359237");

    /**
     * Every unit written after a weight, in the order they are tried, so {@code tonnes} is read
     * before {@code t} and {@code lbs} before {@code lb}.
     */
    private static final List<Map.Entry<String, Unit>> SUFFIXES = List.of(
            Map.entry("pounds", Unit.POUND),
            Map.entry("lbs.", Unit.POUND),
            Map.entry("lbs", Unit.POUND),
            Map.entry("lb.", Unit.POUND),
            Map.entry("lb", Unit.POUND),
            Map.entry("#", Unit.POUND),
            Map.entry("tons", Unit.TON),
            Map.entry("ton", Unit.TON),
            Map.entry("kilograms", Unit.KILOGRAM),
            Map.entry("kg", Unit.KILOGRAM),
            Map.entry("tonnes", Unit.TONNE),
            Map.entry("t", Unit.TONNE));

    public static Pounds ofTons(int tons) {
        return new Pounds(tons * PER_TON);
    }

    /**
     * Read a weight off an application, commas and unit and all: {@code 132,400 lb}, {@code
     * 132400#}, {@code 40 tons}, {@code 36,300 kg}, {@code 36.3 t}.
     *
     * <p>Pounds and short tons are whole numbers or nothing, because nobody weighs a load to the
     * tenth of a pound and a tenth of a ton is a guess. A metric weight may carry up to three
     * places, since the scale prints them, and it is read up to the next whole pound: rounding it
     * down would hand the hauler a pound the scale never gave him. A bare number is pounds.
     *
     * @throws Unreadable when the text is not a weight in any of those units
     */
    public static Pounds parse(String text) {
        String cleaned = text.strip().toLowerCase(Locale.ROOT).replace(",", "");
        for (Map.Entry<String, Unit> suffix : SUFFIXES) {
            if (cleaned.endsWith(suffix.getKey())) {
                String number = cleaned.substring(
                                0, cleaned.length() - suffix.getKey().length())
                        .strip();
                return suffix.getValue().read(number, text);
            }
        }
        return Unit.POUND.read(cleaned, text);
    }

    /** A unit a weight is written in, and how a number in it becomes pounds. */
    private enum Unit {
        POUND(1),
        TON(PER_TON),
        KILOGRAM(1),
        TONNE(1000);

        private final int multiplier;

        Unit(int multiplier) {
            this.multiplier = multiplier;
        }

        private boolean metric() {
            return this == KILOGRAM || this == TONNE;
        }

        Pounds read(String number, String text) {
            if (!metric()) {
                if (!Written.isDigits(number)) {
                    throw unreadable(text);
                }
                return new Pounds(Integer.parseInt(number) * multiplier);
            }
            if (!number.matches("\\d+(\\.\\d{1,3})?")) {
                throw unreadable(text);
            }
            BigDecimal kilograms = new BigDecimal(number).multiply(BigDecimal.valueOf(multiplier));
            return new Pounds(kilograms
                    .divide(KILOGRAMS_PER_POUND, 0, RoundingMode.CEILING)
                    .intValueExact());
        }
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
