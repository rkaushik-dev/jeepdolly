package io.github.rkaushikdev.jeepdolly.routing;

import static io.github.rkaushikdev.jeepdolly.support.RoadLog.roadLog;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Miles;
import io.github.rkaushikdev.jeepdolly.domain.HighwayClass;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Chargeable mileage, which is rounded by class and never by the total. */
class MileageTest {

    private static final HighwayClass ARTERIAL = HighwayClass.ARTERIAL;
    private static final HighwayClass STATE_AID = HighwayClass.STATE_AID;
    private static final List<String> ACROSS = List.of("US201-1", "US201-2", "US2-1", "SA148-1", "SR43-1");

    private static Itinerary trip() {
        return trip("waterville", ACROSS);
    }

    private static Itinerary trip(String origin, List<String> idents) {
        return new Itinerary(roadLog(), origin, idents);
    }

    @Test
    void eachClassIsRoundedUpOnItsOwnAndThenAdded() {
        Mileage bill = Mileage.chargeable(trip());
        assertThat(bill.on(ARTERIAL)).isEqualTo(34);
        assertThat(bill.on(STATE_AID)).isEqualTo(10);
        assertThat(bill.charged()).isEqualTo(44);
        assertThat(bill.measured()).isEqualTo(Miles.of(43, 20));
    }

    @Test
    void roundingTheTotalInsteadWouldHaveChargedAMileLess() {
        Mileage bill = Mileage.chargeable(trip());
        assertThat(bill.measured().roundedUp()).isEqualTo(44);
        assertThat(bill.charged()).isEqualTo(44);
        Mileage mixed = Mileage.chargeable(trip("waterville", List.of("I95-1", "US201-2")));
        assertThat(mixed.measured()).isEqualTo(Miles.of(17));
        assertThat(mixed.measured().roundedUp()).isEqualTo(17);
        assertThat(mixed.charged()).isEqualTo(18);
    }

    @Test
    void thePartsNameTheClassesTheTripTouched() {
        Mileage bill = Mileage.chargeable(trip());
        assertThat(bill.classes()).containsExactly(ARTERIAL, STATE_AID);
        assertThat(bill.on(HighwayClass.INTERSTATE)).isEqualTo(0);
        assertThat(bill.parts().get(1)).hasToString("state aid: 9.40 mi measured, 10 charged");
    }

    @Test
    void aShortTripIsChargedTheMinimumAndSaysSo() {
        Mileage bill = Mileage.chargeable(trip("skowhegan", List.of("US2-1")));
        assertThat(bill.measured()).isEqualTo(Miles.of(6, 20));
        assertThat(bill.charged()).isEqualTo(10);
        assertThat(bill.minimumApplied()).isTrue();
        assertThat(bill).hasToString("6.20 mi measured, 10 miles charged (minimum)");
    }

    @Test
    void aTripPastTheMinimumIsChargedWhatItMeasures() {
        Mileage bill = Mileage.chargeable(trip());
        assertThat(bill.minimumApplied()).isFalse();
        assertThat(bill).hasToString("43.20 mi measured, 44 miles charged");
    }

    @Test
    void theMinimumIsAnArgumentBecauseItIsInTheSchedule() {
        Mileage bill = Mileage.chargeable(trip("skowhegan", List.of("US2-1")), 0);
        assertThat(bill.charged()).isEqualTo(7);
        assertThat(bill.minimumApplied()).isFalse();
    }

    @Test
    void aNegativeMinimumIsRefused() {
        assertThatThrownBy(() -> Mileage.chargeable(trip(), -1)).isInstanceOf(Impossible.class);
    }

    @Test
    void aTownWayIsDrivenAndNotChargedFor() {
        Itinerary route = trip("skowhegan", List.of("US201-3", "TR104-1", "SR8-1"));
        Mileage bill = Mileage.chargeable(route);
        assertThat(bill.classes()).containsExactly(ARTERIAL);
        assertThat(bill.on(HighwayClass.TOWN_WAY)).isEqualTo(0);
        assertThat(Mileage.unpermittableMiles(route)).isEqualTo(Miles.of(3, 20));
        assertThat(Mileage.unpermittableMiles(trip())).isEqualTo(new Miles(0));
    }

    @Test
    void theInterstateIsChargedLikeAnythingElseAtItsOwnRate() {
        Mileage bill = Mileage.chargeable(trip("waterville", List.of("I95-1", "I95-2")));
        assertThat(bill.classes()).containsExactly(HighwayClass.INTERSTATE);
        assertThat(bill.on(HighwayClass.INTERSTATE)).isEqualTo(19);
        assertThat(bill.charged()).isEqualTo(19);
    }
}
