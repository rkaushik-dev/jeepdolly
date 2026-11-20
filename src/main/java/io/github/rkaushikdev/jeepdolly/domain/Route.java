package io.github.rkaushikdev.jeepdolly.domain;

import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Unreadable;
import io.github.rkaushikdev.jeepdolly.Written;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A route designation, parsed so two spellings become one.
 *
 * <p>A route written three ways is a route that cannot be joined to the road log, so I-95, I 95 and
 * i95 all read back as the same route. Routes sort by prefix, then number, then suffix, for a
 * report.
 *
 * @param kind the prefix: I, US, SR, SA or TR
 * @param suffix a letter the road log carries, such as the A of SR 27A, or empty
 */
public record Route(String kind, int number, String suffix) implements Comparable<Route> {

    /** How a designation prefix maps onto a class of road. */
    public static final Map<String, HighwayClass> PREFIX_CLASS = prefixes();

    private static final Pattern ROUTE = Pattern.compile(
            "^\\s*(?<kind>I|US|SR|SA|TR)\\s*[- ]?\\s*(?<number>\\d{1,3})(?<suffix>[A-Z])?\\s*$",
            Pattern.CASE_INSENSITIVE);

    private static final Comparator<Route> ORDER =
            Comparator.comparing(Route::kind).thenComparingInt(Route::number).thenComparing(Route::suffix);

    public Route {
        if (!PREFIX_CLASS.containsKey(kind)) {
            throw new Impossible(Written.quoted(kind) + " is not a route prefix");
        }
        if (number < 1 || number > 999) {
            throw new Impossible(number + " is not a route number");
        }
    }

    /** A route with no letter suffix. */
    public Route(String kind, int number) {
        this(kind, number, "");
    }

    /**
     * Read I-95, US 2, SR-27, sr27, all to the same route.
     *
     * @throws Unreadable when the text is not a designation the road log uses
     */
    public static Route parse(String text) {
        Matcher found = ROUTE.matcher(text);
        if (!found.matches()) {
            throw new Unreadable("cannot read " + Written.quoted(text) + " as a route", Facts.of("written", text));
        }
        String suffix = found.group("suffix");
        return new Route(
                found.group("kind").toUpperCase(Locale.ROOT),
                Integer.parseInt(found.group("number")),
                suffix == null ? "" : suffix.toUpperCase(Locale.ROOT));
    }

    public HighwayClass highwayClass() {
        return PREFIX_CLASS.get(kind);
    }

    @Override
    public int compareTo(Route other) {
        return ORDER.compare(this, other);
    }

    /** With the dash only where the signs use one: {@code I-95}, {@code US 2}. */
    @Override
    public String toString() {
        String joiner = kind.equals("I") ? "-" : " ";
        return kind + joiner + number + suffix;
    }

    private static Map<String, HighwayClass> prefixes() {
        Map<String, HighwayClass> table = new LinkedHashMap<>();
        table.put("I", HighwayClass.INTERSTATE);
        table.put("US", HighwayClass.ARTERIAL);
        table.put("SR", HighwayClass.ARTERIAL);
        table.put("SA", HighwayClass.STATE_AID);
        table.put("TR", HighwayClass.TOWN_WAY);
        return Map.copyOf(table);
    }
}
