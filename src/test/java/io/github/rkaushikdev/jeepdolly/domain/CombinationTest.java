package io.github.rkaushikdev.jeepdolly.domain;

import static io.github.rkaushikdev.jeepdolly.support.Streams.jeepedLowboy;
import static io.github.rkaushikdev.jeepdolly.support.Streams.tractorLowboy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Pounds;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The equipment string, and making it agree with the axles under it. */
class CombinationTest {

    private static final Envelope BOX = Envelope.read("12'", "13'6\"", "82'", "0", "6'");

    private static Unit unit(UnitKind kind, int axles) {
        return unit(kind, axles, "ME 4471", 18000);
    }

    private static Unit unit(UnitKind kind, int axles, String plate) {
        return unit(kind, axles, plate, 18000);
    }

    private static Unit unit(UnitKind kind, int axles, String plate, int tare) {
        return new Unit(kind, axles, plate, new Pounds(tare));
    }

    private static Combination simple() {
        return new Combination(
                List.of(
                        unit(UnitKind.TRACTOR, 3, "ME 4471", 19000).withUnitNumber("207"),
                        unit(UnitKind.LOWBOY, 3, "ME 7712", 24000)),
                tractorLowboy(),
                BOX);
    }

    private static Combination withJeep() {
        return new Combination(
                List.of(
                        unit(UnitKind.TRACTOR, 3, "ME 4471", 19000),
                        unit(UnitKind.JEEP, 2, "ME 8801", 9000),
                        unit(UnitKind.LOWBOY, 3, "ME 7712", 24000),
                        unit(UnitKind.BOOSTER, 1, "ME 8802", 5000)),
                jeepedLowboy(),
                BOX);
    }

    @Test
    void aUnitPrintsByItsNumberWhenItHasOne() {
        assertThat(unit(UnitKind.TRACTOR, 3).withUnitNumber("207")).hasToString("tractor 207 (3 axle)");
        assertThat(unit(UnitKind.LOWBOY, 3, "ME 7712")).hasToString("lowboy ME 7712 (3 axle)");
    }

    @Test
    void aUnitNeedsAxlesAPlateAndAnHonestTare() {
        assertThatThrownBy(() -> unit(UnitKind.LOWBOY, 0)).isInstanceOf(Impossible.class);
        assertThatThrownBy(() -> unit(UnitKind.LOWBOY, 3, "  ")).isInstanceOf(Impossible.class);
        assertThatThrownBy(() -> unit(UnitKind.LOWBOY, 3, "ME 4471", -1)).isInstanceOf(Impossible.class);
    }

    @Test
    void aCombinationReadsBackTheWayAHaulerSaysIt() {
        assertThat(simple().describe()).isEqualTo("tractor, lowboy");
        assertThat(withJeep().describe()).isEqualTo("tractor, jeep dolly, lowboy, booster");
        assertThat(simple()).hasToString("tractor, lowboy, 6 axles, 94,000 lb");
    }

    @Test
    void theGrossIsTheStreamAndThePayloadIsWhatIsLeft() {
        Combination combination = simple();
        assertThat(combination.gross()).isEqualTo(new Pounds(94000));
        assertThat(combination.tare()).isEqualTo(new Pounds(43000));
        assertThat(combination.payload()).isEqualTo(new Pounds(51000));
    }

    @Test
    void theEquipmentAxleCountHasToMatchTheStream() {
        Impossible refusal = catchThrowableOfType(
                Impossible.class,
                () -> new Combination(
                        List.of(unit(UnitKind.TRACTOR, 3), unit(UnitKind.LOWBOY, 4)), tractorLowboy(), BOX));
        assertThat(refusal.facts()).isEqualTo(Map.of("declared", 7, "stream", 6));
    }

    @Test
    void aCombinationWithNothingToPullItIsRefused() {
        assertThatThrownBy(() -> new Combination(List.of(unit(UnitKind.LOWBOY, 6)), tractorLowboy(), BOX))
                .isInstanceOf(Impossible.class);
    }

    @Test
    void twoPowerUnitsAreNotACombinationThisOfficeWrites() {
        assertThatThrownBy(() -> new Combination(
                        List.of(unit(UnitKind.TRACTOR, 3), unit(UnitKind.TRUCK, 3)), tractorLowboy(), BOX))
                .isInstanceOf(Impossible.class)
                .hasMessageContaining("one power unit");
    }

    @Test
    void aStringWithNothingToPutTheLoadOnIsRefused() {
        assertThatThrownBy(() -> new Combination(
                        List.of(unit(UnitKind.TRACTOR, 3), unit(UnitKind.JEEP, 2), unit(UnitKind.BOOSTER, 1)),
                        tractorLowboy(),
                        BOX))
                .isInstanceOf(Impossible.class);
    }

    @Test
    void aJeepBehindTheTrailerIsTheFormFilledInBackwards() {
        assertThatThrownBy(() -> new Combination(
                        List.of(
                                unit(UnitKind.TRACTOR, 3),
                                unit(UnitKind.LOWBOY, 3),
                                unit(UnitKind.JEEP, 2),
                                unit(UnitKind.BOOSTER, 1)),
                        jeepedLowboy(),
                        BOX))
                .isInstanceOf(Impossible.class)
                .hasMessageContaining("ahead of the trailer");
    }

    @Test
    void aBoosterAheadOfTheTrailerIsRefusedTheSameWay() {
        assertThatThrownBy(() -> new Combination(
                        List.of(
                                unit(UnitKind.TRACTOR, 3),
                                unit(UnitKind.BOOSTER, 1),
                                unit(UnitKind.JEEP, 2),
                                unit(UnitKind.LOWBOY, 3)),
                        jeepedLowboy(),
                        BOX))
                .isInstanceOf(Impossible.class)
                .hasMessageContaining("behind the trailer");
    }

    @Test
    void thePartsOfTheStringCanBeAskedForByWhatTheyAre() {
        Combination combination = withJeep();
        assertThat(combination.powerUnit().kind()).isSameAs(UnitKind.TRACTOR);
        assertThat(combination.deck().kind()).isSameAs(UnitKind.LOWBOY);
        assertThat(combination.jeeps()).hasSize(1);
        assertThat(combination.boosters()).hasSize(1);
        assertThat(combination.axleCount()).isEqualTo(9);
        assertThat(combination.plates()).containsExactly("ME 4471", "ME 8801", "ME 7712", "ME 8802");
    }

    @Test
    void theKindsKnowWhatTheyDoInTheString() {
        assertThat(UnitKind.TRACTOR.powered()).isTrue();
        assertThat(UnitKind.JEEP.powered()).isFalse();
        assertThat(UnitKind.RGN.carriesLoad()).isTrue();
        assertThat(UnitKind.BOOSTER.carriesLoad()).isFalse();
        assertThat(UnitKind.RGN).hasToString("removable gooseneck");
    }

    @Test
    void anEmptyCombinationIsRefusedBeforeAnythingIsAskedOfIt() {
        assertThatThrownBy(() -> new Combination(List.of(), tractorLowboy(), BOX))
                .isInstanceOf(Impossible.class);
    }
}
