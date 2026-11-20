package io.github.rkaushikdev.jeepdolly;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An amount of money, held as whole cents.
 *
 * <p>A permit fee is a small number of dollars and it still has to be exact, because the office
 * reconciles what it took in against what it wrote every night and a cent adrift is a night
 * somebody spends looking for it. No floating point value ever holds a fee, not even for a moment,
 * and there is no constructor that takes one.
 *
 * <p>Two operations here are not obvious and both are about division. {@link #split} divides a fee
 * among several payers so that the parts add back to the whole, the remainder going to the earliest
 * parts one cent each: {@code $10.00} split three ways is {@code $3.34}, {@code $3.33}, {@code
 * $3.33}. {@link #percent} takes a whole percent and rounds half up, which is what the fee schedule
 * means by a ten percent surcharge. Rounding down would let a hauler shave a cent by asking for two
 * permits instead of one.
 */
public record Dollars(long cents) implements Comparable<Dollars> {

    public static final int CENTS_PER_DOLLAR = 100;

    /** Nothing owed and nothing paid. */
    public static final Dollars ZERO = new Dollars(0);

    private static final Pattern AMOUNT =
            Pattern.compile("^\\s*(?<sign>-)?\\$?\\s*(?<dollars>[\\d,]+)(?:\\.(?<cents>\\d{2}))?\\s*$");

    /** Whole dollars. */
    public static Dollars of(long dollars) {
        return of(dollars, 0);
    }

    /** Dollars and cents. */
    public static Dollars of(long dollars, long cents) {
        return new Dollars(dollars * CENTS_PER_DOLLAR + cents);
    }

    /**
     * Read an amount off a schedule or a remittance: {@code $1,250.00}, {@code 40.25}, {@code
     * -$15.50}.
     *
     * @throws Unreadable when the text is not an amount with no places or exactly two
     */
    public static Dollars parse(String text) {
        Matcher found = AMOUNT.matcher(text);
        if (!found.matches()) {
            throw new Unreadable("cannot read " + Written.quoted(text) + " as an amount", Facts.of("written", text));
        }
        long dollars = Long.parseLong(found.group("dollars").replace(",", ""));
        String part = found.group("cents");
        long total = dollars * CENTS_PER_DOLLAR + (part == null ? 0 : Long.parseLong(part));
        return new Dollars(found.group("sign") != null ? -total : total);
    }

    /** Add up amounts, which is done often enough to name. Nothing adds up to nothing. */
    public static Dollars total(Iterable<Dollars> amounts) {
        Dollars running = ZERO;
        for (Dollars amount : amounts) {
            running = running.plus(amount);
        }
        return running;
    }

    /** Dollars, rounded toward zero, for a line that prints no cents. */
    public long wholeDollars() {
        return cents / CENTS_PER_DOLLAR;
    }

    /**
     * A whole percent of this amount, rounded half up.
     *
     * @throws Impossible for a negative share
     */
    public Dollars percent(int share) {
        if (share < 0) {
            throw new Impossible("a share of an amount is not negative");
        }
        return new Dollars(Math.floorDiv(cents * share + 50, 100));
    }

    /**
     * Divide into parts that add back to the whole, biggest first.
     *
     * @throws Impossible for fewer than one way
     */
    public List<Dollars> split(int ways) {
        if (ways < 1) {
            throw new Impossible("an amount is split at least one way");
        }
        long base = Math.floorDiv(cents, ways);
        long remainder = Math.floorMod(cents, ways);
        List<Dollars> parts = new ArrayList<>(ways);
        for (int index = 0; index < ways; index++) {
            parts.add(new Dollars(base + (index < remainder ? 1 : 0)));
        }
        return List.copyOf(parts);
    }

    public Dollars plus(Dollars other) {
        return new Dollars(cents + other.cents);
    }

    public Dollars minus(Dollars other) {
        return new Dollars(cents - other.cents);
    }

    public Dollars times(int count) {
        return new Dollars(cents * count);
    }

    public Dollars negated() {
        return new Dollars(-cents);
    }

    @Override
    public int compareTo(Dollars other) {
        return Long.compare(cents, other.cents);
    }

    /** With a dollar sign, commas and two places: {@code $1,250.00}, {@code -$15.50}. */
    @Override
    public String toString() {
        String sign = cents < 0 ? "-" : "";
        long magnitude = Math.abs(cents);
        return String.format(
                Locale.ROOT, "%s$%,d.%02d", sign, magnitude / CENTS_PER_DOLLAR, magnitude % CENTS_PER_DOLLAR);
    }

    /** The amount as it would be written in code, for a debugging line. */
    public String repr() {
        return "Dollars(" + cents + ")";
    }
}
