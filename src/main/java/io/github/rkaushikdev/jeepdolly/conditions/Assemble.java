package io.github.rkaushikdev.jeepdolly.conditions;

import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.escorts.Escorts;
import io.github.rkaushikdev.jeepdolly.escorts.Marking;
import io.github.rkaushikdev.jeepdolly.escorts.Naming;
import io.github.rkaushikdev.jeepdolly.routing.Obstruction;
import io.github.rkaushikdev.jeepdolly.routing.RouteReading;
import io.github.rkaushikdev.jeepdolly.structures.StructureFinding;
import io.github.rkaushikdev.jeepdolly.structures.StructureReading;
import io.github.rkaushikdev.jeepdolly.structures.Verdict;
import io.github.rkaushikdev.jeepdolly.travel.TravelLimits;
import io.github.rkaushikdev.jeepdolly.utilities.Lift;
import io.github.rkaushikdev.jeepdolly.utilities.LineReading;
import java.util.List;
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
 * <p>A permit can only carry one of each code, so a route with two tight corners on it prints one
 * stop traffic condition naming the first. That is a known shortcoming of collecting by code and it
 * has not bitten yet, because a route with two of them is a route the counter has already
 * telephoned about.
 *
 * <p>There is one piece of judgement and it is about duplication. Several rules can produce the same
 * condition: a superload is daylight only, and so is anything over twelve feet wide, and a permit
 * that says daylight hours only twice reads like a mistake. So conditions are collected by code and
 * the first facts win, which is why the general conditions are added last.
 */
public final class Assemble {

    /** On every permit, whatever it is for. */
    public static final List<String> ALWAYS = List.of("R-01", "Q-01", "G-01", "G-02", "G-03");

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

    private static void route(Sheet sheet, RouteReading route) {
        for (Obstruction trouble : route.conditions()) {
            switch (trouble.kind()) {
                case "posted season" -> {
                    String note = trouble.note();
                    sheet.add(
                            "R-02",
                            Facts.of(
                                    "segment",
                                    trouble.segment(),
                                    "limit",
                                    trouble.allowed(),
                                    "order",
                                    note.substring(note.lastIndexOf(' ') + 1)));
                }
                case "curfew" -> sheet.add("T-05", Facts.of("segment", trouble.segment(), "curfew", trouble.allowed()));
                case "tight corner" -> {
                    // Both of these, and in this order. The first is what the driver has to do and
                    // the second is why, and an officer at the roadside reads the first one on its
                    // own.
                    sheet.add("R-05", Facts.of("segment", trouble.segment(), "place", trouble.place()));
                    sheet.add("R-06", Facts.of("place", trouble.place(), "allowed", trouble.allowed()));
                }
                default -> {
                    // Every other kind of condition on a route is printed by the rule that owns
                    // it, not by the route.
                }
            }
        }
    }

    private static void structures(Sheet sheet, StructureReading structures) {
        for (StructureFinding finding : structures.findings()) {
            if (finding.verdict() != Verdict.CONDITIONAL) {
                continue;
            }
            String name = finding.structure().name();
            for (String code : List.of("S-01", "S-02", "S-03", "S-04")) {
                sheet.add(code, Facts.of("structure", name));
            }
        }
    }

    /**
     * One condition per lift, and one saying not to leave without them.
     *
     * <p>This is the one place a condition is added more than once, and it works because each lift
     * carries a different place in its facts. The sheet collects by code and would keep only the
     * first, so the code is made unique per place before it is added. That is a small lie told to
     * the sheet and it is the alternative to a permit that names one of three lifts.
     */
    private static void lines(Sheet sheet, LineReading lines) {
        for (Lift lift : lines.lifts()) {
            sheet.add(
                    "U-01/" + lift.crossing().ident(),
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
            sheet.add("E-01", Facts.of("count", escorts.front()));
        }
        if (escorts.rear() != 0) {
            sheet.add("E-02", Facts.of("count", escorts.rear()));
        }
        if (escorts.heightPole()) {
            sheet.add("E-03");
        }
        if (escorts.police()) {
            sheet.add("E-04");
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

    private static void marking(Sheet sheet, Marking marking) {
        if (marking.banners() != 0) {
            sheet.add("M-01");
        }
        if (marking.flags() != 0) {
            sheet.add("M-02", Facts.of("count", marking.flags()));
        }
        if (marking.lamps() != 0) {
            sheet.add("M-03", Facts.of("count", marking.lamps()));
        }
        if (marking.beacons() != 0) {
            sheet.add("M-04", Facts.of("count", marking.beacons()));
        }
    }
}
