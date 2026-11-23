package io.github.rkaushikdev.jeepdolly.escorts;

import static io.github.rkaushikdev.jeepdolly.support.RoadLog.roadLog;
import static io.github.rkaushikdev.jeepdolly.support.Streams.make;
import static io.github.rkaushikdev.jeepdolly.support.Structures.structureBook;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.domain.AxleStream;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import io.github.rkaushikdev.jeepdolly.domain.HighwayClass;
import io.github.rkaushikdev.jeepdolly.domain.SizeLine;
import io.github.rkaushikdev.jeepdolly.escorts.EscortRule.Post;
import io.github.rkaushikdev.jeepdolly.routing.Itinerary;
import io.github.rkaushikdev.jeepdolly.structures.Analysis;
import io.github.rkaushikdev.jeepdolly.structures.StructureReading;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The escort table, and the two places the class of road changes the answer. */
class EscortsTest {

    private static final List<HighwayClass> ARTERIAL = List.of(HighwayClass.ARTERIAL);
    private static final List<HighwayClass> INTERSTATE = List.of(HighwayClass.INTERSTATE);
    private static final List<HighwayClass> MIXED = List.of(HighwayClass.INTERSTATE, HighwayClass.ARTERIAL);
    private static final EscortRules TABLE = EscortRules.STANDARD;

    private static Envelope load(String width, String height, String length, String rear) {
        return Envelope.read(width, height, length, "0", rear);
    }

    private static Envelope load() {
        return load("10'", "13'6", "80'", "0");
    }

    private static Envelope wide(String width) {
        return load(width, "13'6", "80'", "0");
    }

    private static Envelope tall(String height) {
        return load("10'", height, "80'", "0");
    }

    private static Envelope lengthy(String length) {
        return load("10'", "13'6", length, "0");
    }

    @Test
    void aSmallLoadNeedsNobody() {
        Escorts escorts = TABLE.required(load(), ARTERIAL);
        assertThat(escorts.any()).isFalse();
        assertThat(escorts.total()).isEqualTo(0);
        assertThat(escorts).hasToString("no escorts");
        assertThat(escorts.conditions()).isEmpty();
    }

    @Test
    void pastTwelveFeetSomebodyRidesInFront() {
        Escorts escorts = TABLE.required(wide("13'"), ARTERIAL);
        assertThat(escorts.front()).isEqualTo(1);
        assertThat(escorts.rear()).isEqualTo(0);
        assertThat(escorts.police()).isFalse();
        assertThat(escorts).hasToString("1 front");
        assertThat(escorts.reasons()).containsExactly("width over 12 ft");
    }

    @Test
    void pastFourteenFeetSomebodyRidesBehindAsWell() {
        Escorts escorts = TABLE.required(wide("15'"), ARTERIAL);
        assertThat(escorts.front()).isEqualTo(1);
        assertThat(escorts.rear()).isEqualTo(1);
        assertThat(escorts.police()).isFalse();
        assertThat(escorts).hasToString("1 front, 1 rear");
    }

    @Test
    void pastSixteenFeetATrooperComesToo() {
        Escorts escorts = TABLE.required(wide("17'"), ARTERIAL);
        assertThat(escorts.police()).isTrue();
        assertThat(escorts.toString()).contains("police escort");
        assertThat(escorts.reasons()).contains("width over 16 ft");
    }

    @Test
    void aDividedHighwayDoesNotNeedTheFrontCarForWidth() {
        Escorts escorts = TABLE.required(wide("15'"), INTERSTATE);
        assertThat(escorts.front()).isEqualTo(0);
        assertThat(escorts.rear()).isEqualTo(1);
    }

    @Test
    void aTripThatLeavesTheInterstateNeedsTheFrontCarAgain() {
        Escorts escorts = TABLE.required(wide("15'"), MIXED);
        assertThat(escorts.front()).isEqualTo(1);
        assertThat(escorts.rear()).isEqualTo(1);
    }

    @Test
    void aLongCombinationNeedsARearCarHoweverNarrowItIs() {
        Escorts escorts = TABLE.required(lengthy("110'"), ARTERIAL);
        assertThat(escorts.front()).isEqualTo(0);
        assertThat(escorts.rear()).isEqualTo(1);
        assertThat(escorts.reasons()).containsExactly("length over 100 ft");
    }

    @Test
    void aVeryLongCombinationNeedsOneAtEachEnd() {
        Escorts escorts = TABLE.required(lengthy("130'"), ARTERIAL);
        assertThat(escorts.front()).isEqualTo(1);
        assertThat(escorts.rear()).isEqualTo(1);
    }

    @Test
    void aLongRearOverhangNeedsARearCarOnItsOwn() {
        Escorts escorts = TABLE.required(load("10'", "13'6", "80'", "12'"), ARTERIAL);
        assertThat(escorts.rear()).isEqualTo(1);
        assertThat(escorts.reasons()).containsExactly("rear overhang over 10 ft");
    }

    @Test
    void aTallLoadNeedsAPoleCarAndItGoesInFront() {
        Escorts escorts = TABLE.required(tall("15'"), ARTERIAL);
        assertThat(escorts.front()).isEqualTo(1);
        assertThat(escorts.heightPole()).isTrue();
        assertThat(escorts.toString()).contains("with height pole");
    }

    @Test
    void thePoleCarIsNeededOnTheInterstateToo() {
        Escorts escorts = TABLE.required(tall("15'"), INTERSTATE);
        assertThat(escorts.front()).isEqualTo(1);
        assertThat(escorts.heightPole()).isTrue();
    }

    @Test
    void aSuperloadCarriesATrooperWhateverItMeasures() {
        Escorts escorts = TABLE.required(load(), ARTERIAL, true);
        assertThat(escorts.police()).isTrue();
        assertThat(escorts.total()).isEqualTo(0);
        assertThat(escorts.reasons()).containsExactly("superload");
    }

    @Test
    void twoReasonsForTheSameCarDoNotOrderTwoCars() {
        Escorts escorts = TABLE.required(load("15'", "13'6", "110'", "12'"), ARTERIAL);
        assertThat(escorts.rear()).isEqualTo(1);
        assertThat(escorts.reasons()).hasSize(4);
    }

    @Test
    void theConditionsPrintInTheOrderThePermitFaceWants() {
        Escorts escorts = TABLE.required(load("17'", "15'", "130'", "0"), ARTERIAL);
        List<String> printed = escorts.conditions();
        assertThat(printed.get(0)).startsWith("1 certified pilot car ahead");
        assertThat(printed.get(0)).contains("height pole set one inch above the load");
        assertThat(printed.get(1)).isEqualTo("1 certified pilot car behind the load");
        assertThat(printed.get(2)).startsWith("police escort");
    }

    @Test
    void theThresholdsAreReadFromTheTableAndNotWrittenInTheCode() {
        Escorts justUnder = TABLE.required(wide("12'"), ARTERIAL);
        Escorts justOver = TABLE.required(wide("12'1"), ARTERIAL);
        assertThat(justUnder.any()).isFalse();
        assertThat(justOver.front()).isEqualTo(1);
    }

    @Test
    void aLongFrontOverhangPutsACarInFrontWhereThereIsOncomingTraffic() {
        Envelope beam = Envelope.read("10'", "13'6", "90'", "12'", "0");
        Escorts arterial = TABLE.required(beam, ARTERIAL);
        assertThat(arterial.front()).isEqualTo(1);
        assertThat(arterial.rear()).isEqualTo(0);
        assertThat(arterial.heightPole()).isFalse();
        assertThat(arterial.reasons()).containsExactly("front overhang over 10 ft");
        assertThat(TABLE.required(beam, INTERSTATE).any()).isFalse();
        assertThat(TABLE.required(Envelope.read("10'", "13'6", "90'", "10'", "0"), ARTERIAL)
                        .any())
                .isFalse();
    }

    @Test
    void theTableIsItsLinesInPrintingOrder() {
        assertThat(TABLE.rows()).hasSize(10);
        assertThat(TABLE.rows().get(0)).isEqualTo(EscortRule.undivided("width", Inches.of(12), Post.FRONT));
        assertThat(TABLE.rows().stream().map(EscortRule::reason))
                .containsExactly(
                        "width over 12 ft",
                        "width over 14 ft",
                        "width over 16 ft",
                        "width over 18 ft",
                        "length over 100 ft",
                        "length over 125 ft",
                        "rear overhang over 10 ft",
                        "front overhang over 10 ft",
                        "height over 14 ft 6 in",
                        "height over 16 ft");
    }

    @Test
    void anOrderThatMovesALineMovesTheAnswer() {
        EscortRules ordered = new EscortRules(List.of(
                EscortRule.anyRoad("width", Inches.of(11), Post.FRONT),
                EscortRule.anyRoad("height", Inches.of(14), Post.POLE)));
        Escorts escorts = ordered.required(load("11'6", "14'2", "80'", "0"), INTERSTATE);
        assertThat(escorts.front()).isEqualTo(1);
        assertThat(escorts.heightPole()).isTrue();
        assertThat(escorts.reasons()).containsExactly("width over 11 ft", "height over 14 ft");
        assertThat(TABLE.required(load("11'6", "14'2", "80'", "0"), INTERSTATE).any())
                .isFalse();
    }

    @Test
    void aLineAboutNothingTheEnvelopeMeasuresIsRefused() {
        assertThatThrownBy(() -> EscortRule.anyRoad("weight", Inches.of(12), Post.REAR))
                .isInstanceOf(Impossible.class)
                .hasMessageContaining("weight");
        assertThatThrownBy(() -> EscortRule.anyRoad("width", Inches.ZERO, Post.REAR))
                .isInstanceOf(Impossible.class);
    }

    @Test
    void pastSixteenFeetHighTheRouteIsSurveyedFirst() {
        Escorts escorts = TABLE.required(tall("16'2"), INTERSTATE);
        assertThat(escorts.survey()).isTrue();
        assertThat(escorts.heightPole()).isTrue();
        assertThat(escorts.reasons()).containsExactly("height over 14 ft 6 in", "height over 16 ft");
        assertThat(escorts).hasToString("1 front with height pole, route survey");
        assertThat(escorts.conditions())
                .last()
                .isEqualTo("route surveyed with a height pole and the survey filed before the load moves");
        assertThat(TABLE.required(tall("16'"), INTERSTATE).survey()).isFalse();
    }

    @Test
    void aSuperloadIsSurveyedWhateverItsHeight() {
        Escorts escorts = TABLE.required(load(), ARTERIAL, true);
        assertThat(escorts.survey()).isTrue();
        assertThat(escorts.total()).isEqualTo(0);
        assertThat(escorts).hasToString("police escort, route survey");
        assertThat(escorts.conditions()).hasSize(2);
    }

    @Test
    void escortsWrittenWithoutASurveyHaveNone() {
        assertThat(new Escorts(1, 1, false, false).survey()).isFalse();
        assertThat(new Escorts(0, 0, false, false, true, List.of()).any()).isFalse();
        assertThat(new Escorts(0, 0, false, false, true, List.of())).hasToString("route survey");
        assertThat(new Escorts(0, 0, false, false)).hasToString("no escorts");
    }

    /** The middling lowboy over US 201, which crosses the Kennebec only under conditions. */
    private static StructureReading kennebec() {
        AxleStream middling = make(List.of(12000, 19000, 19000, 25000, 25000, 25000), List.of(200, 54, 180, 52, 52));
        return Analysis.analyseRoute(
                structureBook(), new Itinerary(roadLog(), "waterville", List.of("US201-1", "US201-2")), middling);
    }

    @Test
    void aBridgeCrossedUnderConditionsHasTheTrafficHeldAtBothEnds() {
        assertThat(kennebec().conditional()).hasSize(1);
        Escorts escorts = TABLE.required(load(), ARTERIAL, false, kennebec());
        assertThat(escorts.front()).isEqualTo(1);
        assertThat(escorts.rear()).isEqualTo(1);
        assertThat(escorts.police()).isFalse();
        assertThat(escorts.reasons()).containsExactly("hold traffic at Kennebec River");
    }

    @Test
    void onADividedHighwayOnlyTheFollowingTrafficIsHeld() {
        Escorts escorts = TABLE.required(load(), INTERSTATE, false, kennebec());
        assertThat(escorts.front()).isEqualTo(0);
        assertThat(escorts.rear()).isEqualTo(1);
        assertThat(escorts).hasToString("1 rear");
    }

    @Test
    void aRouteWhoseBridgesAllClearWantsNobodyForThem() {
        AxleStream light = make(List.of(12000, 14000, 14000), List.of(200, 54));
        StructureReading clear = Analysis.analyseRoute(
                structureBook(), new Itinerary(roadLog(), "waterville", List.of("US201-1", "US201-2")), light);
        assertThat(clear.findings()).isNotEmpty();
        assertThat(TABLE.required(load(), ARTERIAL, false, clear)).isEqualTo(TABLE.required(load(), ARTERIAL));
    }

    @Test
    void theHeldTrafficComesAfterTheMeasuresAndBeforeTheSuperload() {
        Escorts escorts = TABLE.required(wide("15'"), ARTERIAL, true, kennebec());
        assertThat(escorts.reasons())
                .containsExactly("width over 12 ft", "width over 14 ft", "hold traffic at Kennebec River", "superload");
        assertThat(escorts.total()).isEqualTo(2);
    }

    @Test
    void pastEighteenFeetWideTwoCarsGoAheadWhereThereIsOncomingTraffic() {
        Escorts escorts = TABLE.required(wide("19'"), ARTERIAL);
        assertThat(escorts.front()).isEqualTo(2);
        assertThat(escorts.rear()).isEqualTo(1);
        assertThat(escorts.total()).isEqualTo(3);
        assertThat(escorts).hasToString("2 front, 1 rear, police escort");
        assertThat(escorts.conditions().get(0)).isEqualTo("2 certified pilot cars ahead of the load");
        assertThat(TABLE.required(wide("18'"), ARTERIAL).front()).isEqualTo(1);
        assertThat(TABLE.required(wide("19'"), INTERSTATE).front()).isEqualTo(0);
    }

    @Test
    void aSecondReasonForTheFrontNeverTakesACarAway() {
        Escorts escorts = TABLE.required(load("19'", "15'", "130'", "0"), ARTERIAL);
        assertThat(escorts.front()).isEqualTo(2);
        assertThat(escorts.heightPole()).isTrue();
        assertThat(escorts.conditions().get(0))
                .isEqualTo(
                        "2 certified pilot cars ahead of the load carrying a height pole set one inch above the load");
    }

    @Test
    void theCountOfCarsReadsTheWayThePermitSaysIt() {
        assertThat(Escorts.cars(1)).isEqualTo("1 certified pilot car");
        assertThat(Escorts.cars(2)).isEqualTo("2 certified pilot cars");
        assertThatThrownBy(() -> new EscortRule(new SizeLine("width", Inches.of(18)), Post.POLICE, false, 2))
                .isInstanceOf(Impossible.class);
        assertThatThrownBy(() -> EscortRule.undivided("width", Inches.of(18), Post.FRONT, 0))
                .isInstanceOf(Impossible.class);
        assertThat(EscortRule.undivided("width", Inches.of(18), Post.FRONT, 2).cars())
                .isEqualTo(2);
    }
}
