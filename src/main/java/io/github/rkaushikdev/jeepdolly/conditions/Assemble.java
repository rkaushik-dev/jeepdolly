package io.github.rkaushikdev.jeepdolly.conditions;

import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.escorts.Escorts;
import io.github.rkaushikdev.jeepdolly.escorts.Marking;
import io.github.rkaushikdev.jeepdolly.escorts.Marking.Worn;
import io.github.rkaushikdev.jeepdolly.escorts.Naming;
import io.github.rkaushikdev.jeepdolly.routing.Obstruction;
import io.github.rkaushikdev.jeepdolly.routing.RouteReading;
import io.github.rkaushikdev.jeepdolly.structures.StructureFinding;
import io.github.rkaushikdev.jeepdolly.structures.StructureReading;
import io.github.rkaushikdev.jeepdolly.travel.TravelLimits;
import io.github.rkaushikdev.jeepdolly.utilities.Lift;
import io.github.rkaushikdev.jeepdolly.utilities.LineReading;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Turning everything that fired into the conditions a permit prints.
 *
 * <p>This is the join. Every other package produces findings in its own vocabulary: obstructions,
 * ratings, escorts, marking, travel limits. This one turns them all into condition codes with facts,
 * and hands the library a list to render.
 *
 * <p>The rule that keeps it honest is that nothing here decides anything. If the escort table said
 * two pilot cars, this writes two pilot cars. It does not think about whether two is right for a
 * load that narrow, because the escort table has already thought about it and having the thinking
 * in two places is how the two places come to disagree.
 *
 * <p>A condition that names a place is printed once for every place it names. Two tight corners
 * are two stop traffic conditions, two curfews on two segments are two lines, and two bridges in
 * the middle band each get their four crossing conditions. Collecting those by code alone printed
 * the first and lost the rest, which is the kind of mistake a driver finds at the second corner.
 *
 * <p>There is one piece of judgement and it is about duplication. Several rules can produce the same
 * condition: a superload is daylight only, and so is anything over twelve feet wide, and a permit
 * that says daylight hours only twice reads like a mistake. So conditions are collected by code and
 * the first facts win, which is why the general conditions are added last.
 */
public final class Assemble {

    /** On every permit, whatever it is for. */
    public static final List<String> ALWAYS = List.of("R-01", "Q-01", "G-01", "G-02", "G-03");

    /** The library line for each thing a load wears. */
    private static final Map<Worn, String> MARKING = Map.of(
            Worn.BANNERS, "M-01", Worn.FLAGS, "M-02", Worn.LAMPS, "M-03", Worn.RED_LAMPS, "M-06", Worn.BEACONS, "M-04");

    /** On every permit for each structure crossed under conditions, in printing order. */
    public static final List<String> CROSSING = List.of("S-01", "S-02", "S-03", "S-04");

    private Assemble() {}

    /** The library code behind a sheet key, dropping any tail after a slash. */
    public static String base(String code) {
        int slash = code.indexOf('/');
        return slash < 0 ? code : code.substring(0, slash);
    }

    /** Every condition a permit with nothing on its route carries, in printing order. */
    public static List<Condition> assemble(
            ConditionLibrary library, String start, String end, TravelLimits limits, Escorts escorts, Marking marking) {
        return assemble(
                library,
                start,
                end,
                limits,
                escorts,
                marking,
                Optional.empty(),
                Optional.empty(),
                0,
                Optional.empty(),
                Optional.empty());
    }

    /**
     * Every condition this permit carries, in printing order.
     *
     * @param start the first day of travel, as it prints
     * @param end the last day of travel, as it prints
     * @param naming the pilot cars the hauler named, read only when the escort table wanted any
     */
    public static List<Condition> assemble(
            ConditionLibrary library,
            String start,
            String end,
            TravelLimits limits,
            Escorts escorts,
            Marking marking,
            Optional<RouteReading> route,
            Optional<StructureReading> structures,
            int liftableAxles,
            Optional<Naming> naming,
            Optional<LineReading> lines) {
        Sheet sheet = new Sheet(library);
        sheet.add("T-01", Facts.of("start", start, "end", end));
        travel(sheet, limits);
        route.ifPresent(reading -> route(sheet, reading));
        structures.ifPresent(reading -> structures(sheet, reading));
        lines.ifPresent(reading -> lines(sheet, reading));
        escorts(sheet, escorts);
        if (escorts.total() != 0) {
            naming.ifPresent(cards -> naming(sheet, cards));
        }
        marking(sheet, marking);
        if (liftableAxles != 0) {
            sheet.add("Q-02");
        }
        ALWAYS.forEach(sheet::add);
        return sheet.rendered();
    }

    private static void travel(Sheet sheet, TravelLimits limits) {
        if (limits.daylightOnly()) {
            sheet.add("T-02");
        }
        if (limits.noWeekend()) {
            sheet.add("T-03");
        }
        limits.windHoldMph().filter(mph -> mph != 0).ifPresent(mph -> {
            sheet.add("T-06", Facts.of("wind", mph));
            sheet.add("T-07");
        });
    }

    /** Every condition the route owns, once for each segment or corner it names. */
    private static void route(Sheet sheet, RouteReading route) {
        for (Obstruction trouble : route.conditions()) {
            String segment = trouble.segment();
            switch (trouble.kind()) {
                case "posted season" ->
                    sheet.addAt(
                            "R-02",
                            segment,
                            Facts.of("segment", segment, "limit", trouble.allowed(), "order", orderOf(trouble)));
                case "curfew" ->
                    sheet.addAt("T-05", segment, Facts.of("segment", segment, "curfew", trouble.allowed()));
                case "close clearance" ->
                    sheet.addAt("R-04", segment, Facts.of("clearance", trouble.allowed(), "structure", trouble.note()));
                case "close width" ->
                    sheet.addAt(
                            "R-07",
                            segment,
                            Facts.of("structure", trouble.note(), "segment", segment, "width", trouble.allowed()));
                case "tight corner" -> {
                    // Both of these, and in this order. The first is what the driver has to do and
                    // the second is why, and an officer at the roadside reads the first one on its
                    // own.
                    String place = trouble.place();
                    sheet.addAt("R-05", place, Facts.of("segment", segment, "place", place));
                    sheet.addAt("R-06", place, Facts.of("place", place, "allowed", trouble.allowed()));
                }
                default -> {
                    // Every other kind of condition on a route is printed by the rule that owns
                    // it, not by the route.
                }
            }
        }
    }

    /** The thaw order a posted season names, which is the last word of its note. */
    private static String orderOf(Obstruction posted) {
        String note = posted.note();
        return note.substring(note.lastIndexOf(' ') + 1);
    }

    /** The crossing conditions, and a line for the pilot cars holding the traffic, at each bridge. */
    private static void structures(Sheet sheet, StructureReading structures) {
        for (StructureFinding finding : structures.conditional()) {
            String ident = finding.structure().ident();
            Map<String, ?> named = Facts.of("structure", finding.structure().name());
            for (String code : CROSSING) {
                sheet.addAt(code, ident, named);
            }
            sheet.addAt("E-08", ident, named);
        }
    }

    /**
     * One condition per lift, and one saying not to leave without them.
     *
     * <p>Each lift carries a different place in its facts, so each is added at its own crossing and
     * the sheet keeps all of them rather than the first.
     */
    private static void lines(Sheet sheet, LineReading lines) {
        for (Lift lift : lines.lifts()) {
            sheet.addAt(
                    "U-01",
                    lift.crossing().ident(),
                    Facts.of(
                            "place",
                            lift.crossing().segment() + " at " + lift.crossing().place(),
                            "owner",
                            lift.owner().name(),
                            "telephone",
                            lift.owner().telephone()));
        }
        if (!lines.lifts().isEmpty()) {
            sheet.add("U-02");
        }
    }

    private static void escorts(Sheet sheet, Escorts escorts) {
        if (escorts.front() != 0) {
            sheet.add("E-01", Facts.of("cars", Escorts.cars(escorts.front())));
        }
        if (escorts.rear() != 0) {
            sheet.add("E-02", Facts.of("cars", Escorts.cars(escorts.rear())));
        }
        if (escorts.heightPole()) {
            sheet.add("E-03");
        }
        if (escorts.police()) {
            sheet.add("E-04");
        }
        if (escorts.survey()) {
            sheet.add("E-07");
        }
    }

    /** Who is piloting, or a line saying the office has not been told yet. */
    private static void naming(Sheet sheet, Naming naming) {
        if (!naming.good().isEmpty()) {
            sheet.add("E-05", Facts.of("cards", naming.line()));
        }
        if (!naming.complete()) {
            sheet.add("E-06");
        }
    }

    /**
     * One line for each thing the load wears, the count in its facts. The lamps line reads one way
     * when the lamps replace the flags and another when they ride beside them.
     */
    private static void marking(Sheet sheet, Marking marking) {
        marking.worn().forEach((what, count) -> {
            String code = what == Worn.LAMPS && marking.roundTheClock() ? "M-05" : MARKING.get(what);
            sheet.add(code, Facts.of("count", count));
        });
    }
}
