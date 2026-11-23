package io.github.rkaushikdev.jeepdolly.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.Pounds;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Axles, the rubber under them, and what counts as one group. */
class AxleTest {

    private static Axle drive() {
        return drive(17000);
    }

    private static Axle drive(int weight) {
        return new Axle(AxleKind.DRIVE, new Pounds(weight));
    }

    private static Axle trailer() {
        return trailer(16000);
    }

    private static Axle trailer(int weight) {
        return new Axle(AxleKind.TRAILER, new Pounds(weight));
    }

    @Test
    void aGroupIsNamedForHowManyAxlesStandInIt() {
        assertThat(AxleGroup.nameFor(1)).isEqualTo("single");
        assertThat(AxleGroup.nameFor(2)).isEqualTo("tandem");
        assertThat(AxleGroup.nameFor(3)).isEqualTo("tridem");
        assertThat(AxleGroup.nameFor(4)).isEqualTo("quad");
        assertThat(AxleGroup.nameFor(5)).isEqualTo("quint");
        assertThat(AxleGroup.nameFor(8)).isEqualTo("8 axle group");
    }

    @Test
    void aGroupOfNoAxlesIsNotAGroup() {
        assertThatThrownBy(() -> AxleGroup.nameFor(0)).isInstanceOf(Impossible.class);
    }

    @Test
    void anAxlePrintsTheWayTheCounterReadsItAloud() {
        assertThat(drive()).hasToString("drive 17,000 lb on 4 tires");
        assertThat(trailer().withLiftable(true)).hasToString("trailer 16,000 lb on 4 tires liftable");
        assertThat(new Axle(AxleKind.STEER, new Pounds(12000)).withTires(2)).hasToString("steer 12,000 lb on 2 tires");
    }

    @Test
    void theRubberOnTheGroundIsTheWidthTimesTheCount() {
        assertThat(drive().tireInches()).isEqualTo(new Inches(44));
        assertThat(drive().withTires(8).withTireWidth(new Inches(11)).tireInches())
                .isEqualTo(new Inches(88));
        Axle steer = new Axle(AxleKind.STEER, new Pounds(12000), 2, new Inches(16));
        assertThat(steer.tireInches()).isEqualTo(new Inches(32));
    }

    @Test
    void anAxleNeedsAnEvenCountOfAtLeastTwoTires() {
        assertThatThrownBy(() -> drive().withTires(3)).isInstanceOf(Impossible.class);
        assertThatThrownBy(() -> drive().withTires(0)).isInstanceOf(Impossible.class);
    }

    @Test
    void anAxleCannotCarryANegativeWeightOrStandOnNothing() {
        assertThatThrownBy(() -> drive(-1)).isInstanceOf(Impossible.class);
        assertThatThrownBy(() -> drive().withTireWidth(new Inches(0))).isInstanceOf(Impossible.class);
    }

    @Test
    void aSteerAxleDoesNotLiftAndSayingSoIsRefused() {
        assertThatThrownBy(() -> new Axle(AxleKind.STEER, new Pounds(12000), 2, Axle.TIRE_WIDTH, true))
                .isInstanceOf(Impossible.class);
    }

    @Test
    void aGroupCarriesOneSpacingFewerThanItHasAxles() {
        AxleGroup tandem = new AxleGroup(List.of(drive(), drive()), List.of(new Inches(54)));
        assertThat(tandem.count()).isEqualTo(2);
        assertThat(tandem.size()).isEqualTo(2);
        assertThat(tandem.name()).isEqualTo("tandem");
        assertThat(tandem.extent()).isEqualTo(new Inches(54));
    }

    @Test
    void aSingleAxleIsAGroupWithNoSpacingAtAll() {
        AxleGroup single = new AxleGroup(List.of(new Axle(AxleKind.STEER, new Pounds(12000)).withTires(2)));
        assertThat(single.name()).isEqualTo("single");
        assertThat(single.extent()).isEqualTo(new Inches(0));
        assertThat(single.weight()).isEqualTo(new Pounds(12000));
    }

    @Test
    void aGroupAddsUpItsAxlesAndSaysSo() {
        AxleGroup tridem =
                new AxleGroup(List.of(trailer(), trailer(), trailer()), List.of(new Inches(52), new Inches(52)));
        assertThat(tridem.weight()).isEqualTo(new Pounds(48000));
        assertThat(tridem.extent()).isEqualTo(new Inches(104));
        assertThat(tridem).hasToString("tridem 48,000 lb over 8 ft 8 in");
    }

    @Test
    void axlesFurtherApartThanEightFeetAreNotOneGroup() {
        assertThatThrownBy(() -> new AxleGroup(List.of(trailer(), trailer()), List.of(Inches.of(9))))
                .isInstanceOf(Impossible.class)
                .hasMessageContaining("too far apart");
    }

    @Test
    void axlesStandingInTheSamePlaceAreNotTwoAxles() {
        assertThatThrownBy(() -> new AxleGroup(List.of(trailer(), trailer()), List.of(new Inches(0))))
                .isInstanceOf(Impossible.class);
    }

    @Test
    void theSpacingCountHasToMatchTheAxleCount() {
        assertThatThrownBy(() -> new AxleGroup(List.of(trailer(), trailer(), trailer()), List.of(new Inches(52))))
                .isInstanceOf(Impossible.class)
                .hasMessageContaining("need 2 spacings");
    }

    @Test
    void anEmptyGroupIsRefusedBeforeAnythingElseIsAskedOfIt() {
        assertThatThrownBy(() -> new AxleGroup(List.of())).isInstanceOf(Impossible.class);
    }

    @Test
    void aGroupKnowsHowMuchOfItCanBeLiftedOffTheRoad() {
        AxleGroup quad = new AxleGroup(
                List.of(trailer(), trailer().withLiftable(true), trailer().withLiftable(true), trailer()),
                List.of(new Inches(52), new Inches(52), new Inches(52)));
        assertThat(quad.liftableCount()).isEqualTo(2);
        assertThat(quad.name()).isEqualTo("quad");
    }

    @Test
    void theHeaviestAxleInAGroupIsTheOneTheScaleReads() {
        AxleGroup tandem = new AxleGroup(List.of(trailer(16000), trailer(19500)), List.of(new Inches(54)));
        assertThat(tandem.heaviest().weight()).isEqualTo(new Pounds(19500));
    }
}
