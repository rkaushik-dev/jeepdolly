package io.github.rkaushikdev.jeepdolly.conditions;

import static io.github.rkaushikdev.jeepdolly.support.RoadLog.roadLog;
import static io.github.rkaushikdev.jeepdolly.support.Streams.make;
import static io.github.rkaushikdev.jeepdolly.support.Structures.structureBook;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.SeasonBook;
import io.github.rkaushikdev.jeepdolly.SeasonKind;
import io.github.rkaushikdev.jeepdolly.SeasonOrder;
import io.github.rkaushikdev.jeepdolly.domain.AxleStream;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import io.github.rkaushikdev.jeepdolly.domain.Restriction;
import io.github.rkaushikdev.jeepdolly.domain.RestrictionBook;
import io.github.rkaushikdev.jeepdolly.domain.RestrictionKind;
import io.github.rkaushikdev.jeepdolly.escorts.EscortRules;
import io.github.rkaushikdev.jeepdolly.escorts.Marking;
import io.github.rkaushikdev.jeepdolly.limits.StatutoryBook;
import io.github.rkaushikdev.jeepdolly.routing.Evaluate;
import io.github.rkaushikdev.jeepdolly.routing.Itinerary;
import io.github.rkaushikdev.jeepdolly.routing.Obstruction;
import io.github.rkaushikdev.jeepdolly.routing.RouteReading;
import io.github.rkaushikdev.jeepdolly.routing.TurnBook;
import io.github.rkaushikdev.jeepdolly.structures.Analysis;
import io.github.rkaushikdev.jeepdolly.structures.Structure;
import io.github.rkaushikdev.jeepdolly.structures.StructureKind;
import io.github.rkaushikdev.jeepdolly.structures.StructureReading;
import io.github.rkaushikdev.jeepdolly.travel.Windows;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Turning every finding into the conditions the permit prints. */
class AssembleTest {

    private static final ConditionLibrary LIBRARY = ConditionLibrary.standard();
    private static final LocalDate APRIL = LocalDate.of(2025, 4, 8);
    private static final List<String> ACROSS = List.of("US201-1", "US201-2", "US2-1", "SA148-1", "SR43-1");

    private static Envelope load() {
        return load("10'", "80'");
    }

    private static Envelope load(String width) {
        return load(width, "80'");
    }

    private static Envelope load(String width, String length) {
        return Envelope.read(width, "13'6", length, "0", "0");
    }

    private static List<Condition> sheet() {
        return sheet(load(), false, false, Optional.empty(), Optional.empty(), 0);
    }

    private static List<Condition> sheet(Envelope envelope) {
        return sheet(envelope, false, false, Optional.empty(), Optional.empty(), 0);
    }

    private static List<Condition> superload(Envelope envelope) {
        return sheet(envelope, true, false, Optional.empty(), Optional.empty(), 0);
    }

    private static List<Condition> sheet(RouteReading route) {
        return sheet(load(), false, false, Optional.of(route), Optional.empty(), 0);
    }

    private static List<Condition> sheet(StructureReading structures) {
        return sheet(load(), false, false, Optional.empty(), Optional.of(structures), 0);
    }

    /** The conditions of one sheet, from the readings a test supplies. */
    private static List<Condition> sheet(
            Envelope envelope,
            boolean superload,
            boolean night,
            Optional<RouteReading> route,
            Optional<StructureReading> structures,
            int liftable) {
        return Assemble.assemble(
                LIBRARY,
                "2025-04-08",
                "2025-04-12",
                Windows.limitsFor(envelope, superload, false),
                EscortRules.STANDARD.required(envelope, List.of(), superload),
                Marking.forLoad(envelope, night, superload),
                route,
                structures,
                liftable,
                Optional.empty(),
                Optional.empty());
    }

    private static List<String> codes(List<Condition> conditions) {
        return conditions.stream().map(Condition::code).toList();
    }

    private static Condition first(List<Condition> conditions, String code) {
        return conditions.stream()
                .filter(one -> one.code().equals(code))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void everyPermitCarriesTheDatesAndTheGeneralConditions() {
        List<Condition> printed = sheet();
        assertThat(codes(printed).get(0)).isEqualTo("T-01");
        assertThat(printed.get(0).text().toLowerCase(Locale.ROOT)).doesNotContain("the route as written");
        assertThat(codes(printed)).contains("T-01", "R-01", "Q-01", "G-01", "G-02", "G-03");
        assertThat(printed.get(0).text()).isEqualTo("Valid from 2025-04-08 to 2025-04-12 inclusive.");
    }

    @Test
    void aLegalSizeLoadCarriesNothingAboutEscortsOrMarking() {
        List<String> printed = codes(sheet(load("8'", "70'")));
        assertThat(printed).noneMatch(one -> one.startsWith("E-"));
        assertThat(printed).noneMatch(one -> one.startsWith("M-"));
    }

    @Test
    void aWideLoadPicksUpDaylightEscortsAndMarkingTogether() {
        List<String> printed = codes(sheet(load("13'")));
        assertThat(printed).contains("T-02");
        assertThat(printed).contains("E-01");
        assertThat(printed).contains("M-01");
        assertThat(printed).contains("M-02");
    }

    @Test
    void theEscortCountComesFromTheEscortTableAndIsNotReasonedAbout() {
        Condition front = first(sheet(load("15'")), "E-01");
        assertThat(front.text()).isEqualTo("1 certified pilot car ahead of the load.");
    }

    @Test
    void aSuperloadSaysDaylightOnlyOnceAndNotTwice() {
        List<String> printed = codes(superload(load("16'")));
        assertThat(Collections.frequency(printed, "T-02")).isEqualTo(1);
        assertThat(printed).contains("T-03");
        assertThat(printed).contains("E-04");
        assertThat(printed.indexOf("E-04")).isLessThan(printed.indexOf("E-07"));
    }

    @Test
    void aSurveyedLoadPrintsTheSurveyAfterItsEscorts() {
        Condition survey = first(superload(load("13'")), "E-07");
        assertThat(survey.text()).isEqualTo("Survey the route with a height pole and file it before moving.");
        assertThat(codes(sheet(load("13'")))).doesNotContain("E-07");
    }

    @Test
    void aLoadOnTheRoadDayAndNightPrintsTheFlagsAndTheLampsBesideThem() {
        Envelope envelope = Envelope.read("8'", "13'6", "90'", "0", "6'");
        List<Condition> printed = Assemble.assemble(
                LIBRARY,
                "2025-04-08",
                "2025-04-12",
                Windows.limitsFor(envelope),
                EscortRules.STANDARD.required(envelope, List.of()),
                Marking.forTrip(envelope, Windows.limitsFor(envelope), false));
        assertThat(codes(printed)).contains("M-02", "M-05").doesNotContain("M-03");
        assertThat(first(printed, "M-05").text())
                .isEqualTo("2 amber lamps beside the flags, lit from sunset to sunrise.");
        assertThat(codes(printed).indexOf("M-02")).isLessThan(codes(printed).indexOf("M-05"));
        assertThat(codes(printed).indexOf("M-05")).isLessThan(codes(printed).indexOf("M-04"));
    }

    @Test
    void theNightMarkingReplacesTheFlagsLine() {
        List<String> printed = codes(sheet(load("13'"), false, true, Optional.empty(), Optional.empty(), 0));
        assertThat(printed).contains("M-03");
        assertThat(printed).doesNotContain("M-02");
    }

    @Test
    void aLiftableAxleAddsTheConditionThatItStaysDown() {
        assertThat(codes(sheet(load(), false, false, Optional.empty(), Optional.empty(), 2)))
                .contains("Q-02");
        assertThat(codes(sheet())).doesNotContain("Q-02");
    }

    @Test
    void aPostedSeasonOnTheRoutePrintsTheOrderItRunsUnder() {
        SeasonBook seasons = new SeasonBook();
        seasons.file(new SeasonOrder("25-04", "district 4", SeasonKind.THAW, APRIL, LocalDate.of(2025, 5, 2), 65));
        RestrictionBook restrictions = new RestrictionBook();
        restrictions.add(Restriction.builder("R-5", "SA148-1", RestrictionKind.SEASONAL, "spring posting")
                .region("district 4")
                .build());
        RouteReading reading = Evaluate.evaluate(
                new Itinerary(roadLog(), "waterville", ACROSS),
                load(),
                new Pounds(94000),
                APRIL,
                restrictions,
                seasons,
                StatutoryBook.standard());
        Condition posted = first(sheet(reading), "R-02");
        assertThat(posted.text()).isEqualTo("SA148-1 is posted at 61,100 lb while order 25-04 runs.");
    }

    @Test
    void aStructureInTheMiddleBandPrintsAllFourCrossingConditions() {
        AxleStream stream = make(List.of(12000, 19000, 19000, 25000, 25000, 25000), List.of(200, 54, 180, 52, 52));
        StructureReading reading = Analysis.analyseRoute(
                structureBook(), new Itinerary(roadLog(), "waterville", List.of("US201-1", "US201-2")), stream);
        List<String> lines = sheet(reading).stream()
                .filter(one -> one.code().startsWith("S-"))
                .map(Condition::text)
                .toList();
        assertThat(lines).hasSize(4);
        assertThat(lines.get(0)).isEqualTo("Cross Kennebec River alone with no other vehicle on it.");
        assertThat(lines.get(3)).isEqualTo("Do not stop on Kennebec River for any reason.");
    }

    @Test
    void aStructureInTheMiddleBandHasThePilotCarsHoldTheTrafficOffIt() {
        AxleStream stream = make(List.of(12000, 19000, 19000, 25000, 25000, 25000), List.of(200, 54, 180, 52, 52));
        StructureReading reading = Analysis.analyseRoute(
                structureBook(), new Itinerary(roadLog(), "waterville", List.of("US201-1", "US201-2")), stream);
        Condition held = first(sheet(reading), "E-08");
        assertThat(held.text()).isEqualTo("Pilot cars hold all traffic off Kennebec River while the load crosses.");
        assertThat(held.category()).isSameAs(Category.ESCORT);
    }

    @Test
    void aConditionAddedAtTwoPlacesIsKeptTwice() {
        Sheet sheet = new Sheet(LIBRARY);
        sheet.addAt("E-08", "1247", Map.of("structure", "Kennebec River"));
        sheet.addAt("E-08", "5006", Map.of("structure", "Sandy River"));
        sheet.addAt("E-08", "1247", Map.of("structure", "Kennebec River again"));
        assertThat(sheet.codes()).containsExactly("E-08/1247", "E-08/5006");
        assertThat(sheet.rendered())
                .extracting(Condition::text)
                .containsExactly(
                        "Pilot cars hold all traffic off Kennebec River while the load crosses.",
                        "Pilot cars hold all traffic off Sandy River while the load crosses.");
    }

    @Test
    void aStructureThatClearsPrintsNothingAboutItself() {
        AxleStream stream = make(List.of(12000, 14000, 14000), List.of(200, 54));
        StructureReading reading = Analysis.analyseRoute(
                structureBook(), new Itinerary(roadLog(), "waterville", List.of("US201-1", "US201-2")), stream);
        assertThat(codes(sheet(reading))).noneMatch(one -> one.startsWith("S-"));
        assertThat(codes(sheet(reading))).doesNotContain("E-08");
    }

    @Test
    void theConditionsPrintGroupedByCategoryInFaceOrder() {
        List<String> printed = codes(superload(load("16'")));
        assertThat(printed.indexOf("T-01")).isLessThan(printed.indexOf("R-01"));
        assertThat(printed.indexOf("R-01")).isLessThan(printed.indexOf("E-01"));
        assertThat(printed.indexOf("E-01")).isLessThan(printed.indexOf("M-01"));
        assertThat(printed.indexOf("M-01")).isLessThan(printed.indexOf("G-01"));
    }

    private static RouteReading cornerReading() {
        return cornerReading("95'");
    }

    private static RouteReading cornerReading(String length) {
        return Evaluate.evaluate(
                new Itinerary(roadLog(), "waterville", ACROSS),
                load("10'", length),
                new Pounds(94000),
                APRIL,
                new RestrictionBook(),
                new SeasonBook(),
                StatutoryBook.standard(),
                TurnBook.coded());
    }

    @Test
    void aCornerTheLoadOnlyJustGetsRoundPrintsTwoRouteConditions() {
        List<Condition> printed = sheet(cornerReading());
        assertThat(first(printed, "R-05").text()).isEqualTo("Stop traffic both ways to turn onto US2-1 at skowhegan.");
        assertThat(first(printed, "R-06").text())
                .isEqualTo("The turn at skowhegan allows 65 ft radius. Walk it first.");
    }

    @Test
    void theTwoCornerConditionsPrintInTheOrderTheDriverNeedsThem() {
        List<String> printed = codes(sheet(cornerReading()));
        assertThat(printed.indexOf("R-05")).isLessThan(printed.indexOf("R-06"));
        assertThat(printed.indexOf("R-01")).isLessThan(printed.indexOf("R-05"));
    }

    /** A route reading by hand, carrying only the conditions a test names. */
    private static RouteReading reading(Obstruction... conditions) {
        return new RouteReading(APRIL, new Itinerary(roadLog(), "waterville", ACROSS), List.of(conditions));
    }

    private static Obstruction corner(String segment, String place, String allowed) {
        return new Obstruction(segment, "tight corner", "the turn at " + place, "60 ft radius", allowed, false, place);
    }

    @Test
    void twoTightCornersAreTwoStopTrafficConditions() {
        List<Condition> printed = sheet(reading(
                corner("US2-1", "skowhegan", "65 ft radius"), corner("SR43-1", "norridgewock", "62 ft radius")));
        assertThat(printed.stream().filter(one -> one.code().equals("R-05")).map(Condition::text))
                .containsExactly(
                        "Stop traffic both ways to turn onto US2-1 at skowhegan.",
                        "Stop traffic both ways to turn onto SR43-1 at norridgewock.");
        assertThat(printed.stream().filter(one -> one.code().equals("R-06")).map(Condition::text))
                .containsExactly(
                        "The turn at skowhegan allows 65 ft radius. Walk it first.",
                        "The turn at norridgewock allows 62 ft radius. Walk it first.");
    }

    @Test
    void aCurfewAndAPostingOnEachOfTwoSegmentsArePrintedForBoth() {
        List<Condition> printed = sheet(reading(
                new Obstruction("US201-1", "curfew", "school", "travel outside the curfew", "07:00 to 08:30", false),
                new Obstruction("US2-1", "curfew", "mill shift", "travel outside the curfew", "15:00 to 16:00", false),
                new Obstruction("SA148-1", "posted season", "thaw under order 25-04", "94,000 lb", "61,100 lb", false),
                new Obstruction("SR43-1", "posted season", "thaw under order 25-05", "94,000 lb", "58,500 lb", false)));
        assertThat(printed.stream().filter(one -> one.code().equals("T-05")).map(Condition::text))
                .containsExactly(
                        "No movement on US201-1 between 07:00 to 08:30.",
                        "No movement on US2-1 between 15:00 to 16:00.");
        assertThat(printed.stream().filter(one -> one.code().equals("R-02")).map(Condition::text))
                .containsExactly(
                        "SA148-1 is posted at 61,100 lb while order 25-04 runs.",
                        "SR43-1 is posted at 58,500 lb while order 25-05 runs.");
    }

    @Test
    void aCloseClearanceAndANarrowArchPrintWhatTheDriverHasToDoAboutThem() {
        List<Condition> printed = sheet(reading(
                new Obstruction("US2-1", "close clearance", "railroad bridge", "13 ft 9 in", "13 ft 11 in", false),
                new Obstruction("SR43-1", "close width", "stone arch", "10 ft", "10 ft 4 in", false)));
        assertThat(first(printed, "R-04").text())
                .isEqualTo("Obey the posted clearance of 13 ft 11 in at railroad bridge.");
        assertThat(first(printed, "R-07").text())
                .isEqualTo("The stone arch on SR43-1 is 10 ft 4 in wide. Cross it centred, slowly.");
        assertThat(codes(printed).indexOf("R-04")).isLessThan(codes(printed).indexOf("R-07"));
    }

    @Test
    void twoBridgesInTheMiddleBandEachGetTheirFourCrossingConditions() {
        AxleStream stream = make(List.of(12000, 19000, 19000, 25000, 25000, 25000), List.of(200, 54, 180, 52, 52));
        Structure sebasticook = Structure.builder("7001", "Sebasticook River", "US2-1", StructureKind.of("bridge"))
                .spansFeet(List.of(100))
                .built(1958)
                .operatingRating(new Pounds(130000))
                .build();
        StructureReading reading = Analysis.analyse(List.of(structureBook().get("1247"), sebasticook), stream);
        assertThat(reading.conditional()).hasSize(2);
        List<Condition> printed = sheet(reading);
        assertThat(printed.stream().filter(one -> one.code().startsWith("S-"))).hasSize(8);
        assertThat(printed.stream().filter(one -> one.code().equals("S-04")).map(Condition::text))
                .containsExactly(
                        "Do not stop on Kennebec River for any reason.",
                        "Do not stop on Sebasticook River for any reason.");
        assertThat(printed.stream().filter(one -> one.code().equals("E-08"))).hasSize(2);
        assertThat(Assemble.CROSSING).containsExactly("S-01", "S-02", "S-03", "S-04");
    }

    @Test
    void aCornerWithRoomToSpareSaysNothingOnThePermit() {
        assertThat(codes(sheet(cornerReading("80'")))).doesNotContain("R-05");
    }

    @Test
    void aCornerTheLoadCannotGetRoundIsNotAConditionAtAll() {
        // It is a refusal, and a refused application never reaches this file.
        assertThat(codes(sheet(cornerReading("150'")))).doesNotContain("R-05");
    }

    @Test
    void theSheetKeepsTheFirstFactsAndDropsTheTailWhenItAsksForWording() {
        Sheet sheet = new Sheet(LIBRARY);
        sheet.add("T-01", Map.of("start", "2025-04-08", "end", "2025-04-12"));
        sheet.add("T-01", Map.of("start", "2026-01-01", "end", "2026-01-02"));
        sheet.add("U-02/one");
        assertThat(sheet.size()).isEqualTo(2);
        assertThat(sheet.codes()).containsExactly("T-01", "U-02/one");
        assertThat(Assemble.base("U-01/L-4")).isEqualTo("U-01");
        assertThat(codes(sheet.rendered())).containsExactly("T-01", "U-02");
    }
}
