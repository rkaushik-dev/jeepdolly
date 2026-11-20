package io.github.rkaushikdev.jeepdolly.escorts;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import io.github.rkaushikdev.jeepdolly.domain.HighwayClass;
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
}
