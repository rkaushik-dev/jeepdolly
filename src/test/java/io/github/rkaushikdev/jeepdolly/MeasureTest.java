package io.github.rkaushikdev.jeepdolly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.support.Signatures;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The measures, and the fax formats they have to survive. */
class MeasureTest {

    @Test
    void aSizeReadsBackAsFeetAndInches() {
        assertThat(Inches.of(14, 6)).hasToString("14 ft 6 in");
        assertThat(Inches.of(12)).hasToString("12 ft");
        assertThat(new Inches(8)).hasToString("8 in");
        assertThat(new Inches(174).repr()).isEqualTo("Inches(174)");
    }

    @Test
    void everyFormatTheOfficeReceivesMeansTheSameNumber() {
        for (String written : List.of("14'6\"", "14' 6\"", "14 ft 6 in", "14ft6in", "14-6", "174")) {
            assertThat(Inches.parse(written)).as(written).isEqualTo(new Inches(174));
        }
    }

    @Test
    void aSizeWrittenInWholeFeetNeedsNoInchesPart() {
        assertThat(Inches.parse("13'")).isEqualTo(new Inches(156));
        assertThat(Inches.parse("13 ft")).isEqualTo(new Inches(156));
        assertThat(Inches.parse("13 feet")).isEqualTo(new Inches(156));
    }

    @Test
    void aSizeThatCannotBeReadIsRefusedRatherThanGuessed() {
        assertThatThrownBy(() -> Inches.parse("about fourteen feet")).isInstanceOf(Unreadable.class);
        assertThatThrownBy(() -> Inches.parse("14.5")).isInstanceOf(Unreadable.class);
        assertThatThrownBy(() -> Inches.parse("")).isInstanceOf(Unreadable.class);
    }

    @Test
    void aFractionOffTheTapeReadsUpToTheNextWholeInch() {
        for (String written :
                List.of("14'6 1/2\"", "14' 6-1/2\"", "14 ft 6 1/2 in", "14-6 1/2", "174 1/2", "174-1/4\"")) {
            assertThat(Inches.parse(written)).as(written).isEqualTo(new Inches(175));
        }
        assertThat(Inches.parse("14'11 15/16")).isEqualTo(Inches.of(15));
        assertThat(Inches.parse("6 1/16 in")).isEqualTo(new Inches(7));
    }

    @Test
    void aFractionNobodyReadsOffATapeIsRefused() {
        for (String written : List.of("14'6 1/3", "174 2/2", "174 0/2", "174 3/32", "14'61/2")) {
            assertThatThrownBy(() -> Inches.parse(written)).as(written).isInstanceOf(Unreadable.class);
        }
    }

    @Test
    void aRefusedFractionSaysWhatWasWritten() {
        assertThatThrownBy(() -> Inches.parse("14'6 5/3"))
                .isInstanceOf(Unreadable.class)
                .hasMessageContaining("14'6 5/3")
                .hasMessageContaining("as a size");
    }

    @Test
    void aSizeIsWholeInchesAndAFloatIsNotASize() {
        // A fraction or a flag cannot be a size: the only way in takes an int.
        Signatures.constructorsTakeOnly(Inches.class, int.class);
    }

    @Test
    void feetAndTheSpareInchesCarryTheSignTogether() {
        Inches rear = new Inches(-30);
        assertThat(rear.feet()).isEqualTo(-2);
        assertThat(rear.spareInches()).isEqualTo(-6);
        assertThat(rear).hasToString("-2 ft 6 in");
        assertThat(rear.abs()).isEqualTo(new Inches(30));
    }

    @Test
    void sizesAddAndSubtractButNotWithBareNumbers() {
        assertThat(Inches.of(8, 6).plus(new Inches(6))).isEqualTo(Inches.of(9));
        assertThat(Inches.of(9).minus(new Inches(6))).isEqualTo(Inches.of(8, 6));
        assertThat(new Inches(15).times(3)).isEqualTo(new Inches(45));
        // A size never adds a bare number or multiplies by a fraction: the arithmetic takes only
        // its own kind, and a count of whole things.
        Signatures.methodTakesOnly(Inches.class, "plus", Inches.class);
        Signatures.methodTakesOnly(Inches.class, "times", int.class);
    }

    @Test
    void overALimitIsTheExcessAndNeverANegativeExcess() {
        Inches legal = Inches.of(8, 6);
        assertThat(Inches.of(12).over(legal)).isEqualTo(new Inches(42));
        assertThat(Inches.of(8).over(legal)).isEqualTo(new Inches(0));
    }

    @Test
    void sizesSortByTheInchesTheyHold() {
        List<Inches> loads = new ArrayList<>(List.of(Inches.of(14), Inches.of(8, 6), Inches.of(11, 3)));
        assertThat(Collections.max(loads)).isEqualTo(new Inches(168));
        Collections.sort(loads);
        assertThat(loads).containsExactly(Inches.of(8, 6), Inches.of(11, 3), Inches.of(14));
    }

    @Test
    void aWeightReadsBackWithTheCommasTheOfficeWrites() {
        assertThat(new Pounds(132400)).hasToString("132,400 lb");
        assertThat(new Pounds(0)).hasToString("0 lb");
        assertThat(new Pounds(80000).repr()).isEqualTo("Pounds(80000)");
    }

    @Test
    void aWeightParsesOutOfTheShapesThatArrive() {
        assertThat(Pounds.parse("132400")).isEqualTo(new Pounds(132400));
        assertThat(Pounds.parse("132,400")).isEqualTo(new Pounds(132400));
        assertThat(Pounds.parse("132,400 lb")).isEqualTo(new Pounds(132400));
        assertThat(Pounds.parse("132400lbs")).isEqualTo(new Pounds(132400));
        assertThat(Pounds.parse("132400#")).isEqualTo(new Pounds(132400));
    }

    @Test
    void tonsAreReadAndThenNeverStoredAgain() {
        assertThat(Pounds.parse("40 tons")).isEqualTo(new Pounds(80000));
        assertThat(Pounds.parse("1 ton")).isEqualTo(new Pounds(2000));
        assertThat(Pounds.ofTons(66)).isEqualTo(new Pounds(132000));
    }

    @Test
    void aWeightThatCannotBeReadIsRefused() {
        assertThatThrownBy(() -> Pounds.parse("heavy")).isInstanceOf(Unreadable.class);
        assertThatThrownBy(() -> Pounds.parse("40.5 tons")).isInstanceOf(Unreadable.class);
        assertThatThrownBy(() -> Pounds.parse("132.4 lb")).isInstanceOf(Unreadable.class);
    }

    @Test
    void aWeightIsWholePounds() {
        // A fraction of a pound cannot be a weight: the only way in takes an int.
        Signatures.constructorsTakeOnly(Pounds.class, int.class);
    }

    @Test
    void weightsAddSubtractAndMultiplyByACountOfAxles() {
        assertThat(new Pounds(20000).plus(new Pounds(14000))).isEqualTo(new Pounds(34000));
        assertThat(new Pounds(34000).minus(new Pounds(20000))).isEqualTo(new Pounds(14000));
        assertThat(new Pounds(20000).times(4)).isEqualTo(new Pounds(80000));
        assertThat(new Pounds(500).negated()).isEqualTo(new Pounds(-500));
        // A weight never adds a bare number: addition takes only another weight.
        Signatures.methodTakesOnly(Pounds.class, "plus", Pounds.class);
    }

    @Test
    void aWeightOverALimitIsTheOverageThePermitPrices() {
        assertThat(new Pounds(99000).over(new Pounds(80000))).isEqualTo(new Pounds(19000));
        assertThat(new Pounds(72000).over(new Pounds(80000))).isEqualTo(new Pounds(0));
    }

    @Test
    void aDistanceHoldsHundredthsBecauseTheRoadLogDoes() {
        assertThat(Miles.of(12, 40)).isEqualTo(new Miles(1240));
        assertThat(new Miles(1240)).hasToString("12.40 mi");
        assertThat(new Miles(7)).hasToString("0.07 mi");
        assertThat(new Miles(1240).repr()).isEqualTo("Miles(1240)");
    }

    @Test
    void aDistanceParsesOffTheRoadLog() {
        assertThat(Miles.parse("12.40")).isEqualTo(new Miles(1240));
        assertThat(Miles.parse("12.4")).isEqualTo(new Miles(1240));
        assertThat(Miles.parse("12")).isEqualTo(new Miles(1200));
        assertThat(Miles.parse("12.40 mi")).isEqualTo(new Miles(1240));
        assertThat(Miles.parse("12.40 miles")).isEqualTo(new Miles(1240));
    }

    @Test
    void aDistanceWithMoreThanTwoPlacesIsNotOffTheRoadLog() {
        assertThatThrownBy(() -> Miles.parse("12.400")).isInstanceOf(Unreadable.class);
        assertThatThrownBy(() -> Miles.parse("twelve")).isInstanceOf(Unreadable.class);
        // A fraction of a hundredth cannot be a distance: the only way in takes whole hundredths.
        Signatures.constructorsTakeOnly(Miles.class, int.class);
    }

    @Test
    void pricingRoundsTheDistanceUpToTheNextWholeMile() {
        assertThat(Miles.of(12, 40).roundedUp()).isEqualTo(13);
        assertThat(Miles.of(12).roundedUp()).isEqualTo(12);
        assertThat(new Miles(1).roundedUp()).isEqualTo(1);
        assertThat(new Miles(0).roundedUp()).isEqualTo(0);
    }

    @Test
    void distancesAddAlongARoute() {
        List<Miles> legs = List.of(Miles.of(4, 20), Miles.of(11, 90), Miles.of(0, 30));
        Miles total = legs.stream().reduce(new Miles(0), Miles::plus);
        assertThat(total).isEqualTo(Miles.of(16, 40));
        assertThat(total.minus(Miles.of(4, 20))).isEqualTo(Miles.of(12, 20));
    }
}
