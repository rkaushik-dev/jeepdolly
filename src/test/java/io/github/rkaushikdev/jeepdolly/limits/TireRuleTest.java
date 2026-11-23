package io.github.rkaushikdev.jeepdolly.limits;

import static io.github.rkaushikdev.jeepdolly.support.Streams.make;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.domain.Axle;
import io.github.rkaushikdev.jeepdolly.domain.AxleGroup;
import io.github.rkaushikdev.jeepdolly.domain.AxleKind;
import io.github.rkaushikdev.jeepdolly.domain.AxleStream;
import io.github.rkaushikdev.jeepdolly.domain.HighwayClass;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The tire rule, and the cap that holds wide base singles below what their rubber would say. */
class TireRuleTest {

    private static final TireRule ARTERIAL = TireRule.at(600);

    private static Axle drive() {
        return new Axle(AxleKind.DRIVE, new Pounds(17000));
    }

    private static Axle singles(int weight, int width) {
        return new Axle(AxleKind.TRAILER, new Pounds(weight), 2, new Inches(width));
    }

    @Test
    void theTireLimitIsThePublishedRateTimesThatWidth() {
        assertThat(ARTERIAL.limit(drive())).isEqualTo(new Pounds(26400));
        assertThat(ARTERIAL.limit(drive().withTires(2).withTireWidth(new Inches(13))))
                .isEqualTo(new Pounds(15600));
    }

    @Test
    void anAxleOverItsTireLimitReportsTheOverageAndNotAVerdict() {
        Axle narrow = new Axle(AxleKind.JEEP, new Pounds(22000), 4, new Inches(8));
        assertThat(ARTERIAL.limit(narrow)).isEqualTo(new Pounds(19200));
        assertThat(ARTERIAL.over(narrow)).isEqualTo(new Pounds(2800));
        assertThat(ARTERIAL.over(drive())).isEqualTo(new Pounds(0));
    }

    @Test
    void aGroupTireLimitIsTheSumOfWhatItsAxlesWillCarry() {
        AxleGroup tandem = new AxleGroup(List.of(drive().withTires(8), drive().withTires(8)), List.of(new Inches(54)));
        assertThat(ARTERIAL.limit(tandem)).isEqualTo(new Pounds(105600));
        AxleGroup singled = new AxleGroup(List.of(singles(19000, 18), singles(19000, 18)), List.of(new Inches(54)));
        assertThat(ARTERIAL.limit(singled)).isEqualTo(new Pounds(40000));
    }

    @Test
    void wideBaseSinglesCarryNoMoreThanTheCapWhateverTheirRubber() {
        Axle wide = singles(21000, 18);
        assertThat(TireRule.wideBase(wide)).isTrue();
        assertThat(ARTERIAL.limit(wide)).isEqualTo(TireRule.WIDE_BASE_CAP);
        assertThat(ARTERIAL.over(wide)).isEqualTo(new Pounds(1000));
        assertThat(TireRule.at(500).limit(wide)).isEqualTo(new Pounds(18000));
    }

    @Test
    void onlyTwoTiresAtLeastFourteenInchesWideOffTheSteerAreWideBase() {
        assertThat(TireRule.wideBase(singles(15000, 13))).isFalse();
        assertThat(TireRule.wideBase(singles(15000, 14))).isTrue();
        assertThat(TireRule.wideBase(singles(15000, 16).withTires(4))).isFalse();
        Axle steer = new Axle(AxleKind.STEER, new Pounds(21000), 2, new Inches(18));
        assertThat(TireRule.wideBase(steer)).isFalse();
        assertThat(ARTERIAL.limit(steer)).isEqualTo(new Pounds(21600));
    }

    @Test
    void aRuleWithNoRateIsNotARule() {
        assertThatThrownBy(() -> TireRule.at(0)).isInstanceOf(Impossible.class).hasMessageContaining("pounds per inch");
    }

    @Test
    void theWeightReadingHoldsWideBaseSinglesToTheCap() {
        List<Axle> axles =
                List.of(make(List.of(12000), List.of()).axles().get(0), singles(20500, 18), singles(20500, 18));
        AxleStream stream = new AxleStream(axles, List.of(new Inches(200), new Inches(54)));
        StatutoryTable table = StatutoryBook.standard().on(HighwayClass.ARTERIAL, StatutoryBook.STANDARD_EFFECTIVE);
        WeightReading reading = WeightReading.read(stream, table);
        assertThat(reading.tireFailures()).hasSize(2);
        assertThat(reading.tireFailures().get(0).limit()).isEqualTo(new Pounds(20000));
        assertThat(reading.reasons()).contains("axle 2 carries 20,500 lb on tires rated 20,000 lb");
    }
}
