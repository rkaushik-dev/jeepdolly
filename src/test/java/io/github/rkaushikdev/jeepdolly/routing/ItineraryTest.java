package io.github.rkaushikdev.jeepdolly.routing;

import static io.github.rkaushikdev.jeepdolly.support.RoadLog.roadLog;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Miles;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import io.github.rkaushikdev.jeepdolly.domain.HighwayClass;
import io.github.rkaushikdev.jeepdolly.domain.Network;
import io.github.rkaushikdev.jeepdolly.domain.Segment;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** An itinerary: segments in driven order, and what the trip touches. */
class ItineraryTest {

    private static final List<String> ACROSS = List.of("US201-1", "US201-2", "US2-1", "SA148-1", "SR43-1");

    private static Itinerary trip() {
        return trip("waterville", ACROSS);
    }

    private static Itinerary trip(String origin, List<String> idents) {
        return new Itinerary(roadLog(), origin, idents);
    }

    @Test
    void anItineraryJoinsEndToEndAndKnowsWhereItFinishes() {
        Itinerary route = trip();
        assertThat(route.origin()).isEqualTo("waterville");
        assertThat(route.destination()).isEqualTo("pittsfield");
        assertThat(route.size()).isEqualTo(5);
        assertThat(route).hasToString("Itinerary(waterville to pittsfield, 5 legs)");
    }

    @Test
    void theLegsCarryTheDirectionTheyAreDrivenIn() {
        List<Leg> legs = new ArrayList<>();
        trip().forEach(legs::add);
        assertThat(legs.get(0).enteredAt()).isEqualTo("waterville");
        assertThat(legs.get(0).leftAt()).isEqualTo("fairfield");
        assertThat(legs.get(3)).hasToString("SA 148 norridgewock to canaan");
    }

    @Test
    void aSegmentThatDoesNotJoinTheOneBeforeItIsRefused() {
        Impossible refusal =
                catchThrowableOfType(Impossible.class, () -> trip("waterville", List.of("US201-1", "SR43-1")));
        assertThat(refusal.facts()).isEqualTo(Map.of("segment", "SR43-1", "at", "fairfield"));
    }

    @Test
    void anItineraryFromAPlaceThatIsNotOnTheLogIsRefused() {
        assertThatThrownBy(() -> trip("bangor", ACROSS)).isInstanceOf(NotOnFile.class);
    }

    @Test
    void anItineraryNamingASegmentThatIsNotOnTheLogIsRefused() {
        assertThatThrownBy(() -> trip("waterville", List.of("US201-1", "NOPE-1")))
                .isInstanceOf(NotOnFile.class);
    }

    @Test
    void anItineraryWithNoSegmentsAtAllIsRefused() {
        assertThatThrownBy(() -> trip("waterville", List.of())).isInstanceOf(Impossible.class);
    }

    @Test
    void theDistanceIsTheSegmentsAddedUpOffTheRoadLog() {
        assertThat(trip().distance()).isEqualTo(Miles.of(43, 20));
    }

    @Test
    void theDistanceIsKeptPerClassBecauseTheFeeIsPricedThatWay() {
        Itinerary route = trip();
        assertThat(route.distanceOn(HighwayClass.ARTERIAL)).isEqualTo(Miles.of(33, 80));
        assertThat(route.distanceOn(HighwayClass.STATE_AID)).isEqualTo(Miles.of(9, 40));
        assertThat(route.distanceOn(HighwayClass.INTERSTATE)).isEqualTo(new Miles(0));
    }

    @Test
    void theClassesAndDistrictsTouchedAreWhatTheRulesAreReadFrom() {
        Itinerary route = trip();
        assertThat(route.classes()).containsExactly(HighwayClass.ARTERIAL, HighwayClass.STATE_AID);
        assertThat(route.districts()).containsExactly(3, 4);
    }

    @Test
    void theTownsRunFromTheOriginThroughEveryLeg() {
        assertThat(trip().towns())
                .containsExactly("waterville", "fairfield", "skowhegan", "norridgewock", "canaan", "pittsfield");
    }

    @Test
    void aTripCanBeAskedWhetherItTouchesASegment() {
        Itinerary route = trip();
        assertThat(route.touches("SA148-1")).isTrue();
        assertThat(route.touches("TR104-1")).isFalse();
        assertThat(route.idents()).isEqualTo(ACROSS);
    }

    @Test
    void aTripOverATownWayNamesTheSegmentThisOfficeCannotWrite() {
        Itinerary route = trip("skowhegan", List.of("US201-3", "TR104-1", "SR8-1"));
        assertThat(route.destination()).isEqualTo("norridgewock");
        assertThat(route.unpermittable()).containsExactly("TR104-1");
        assertThat(trip().unpermittable()).isEmpty();
    }

    @Test
    void theRoutePrintsOnThePermitFaceAsADriverWouldReadIt() {
        assertThat(trip().narrative())
                .isEqualTo("from Waterville, US 201 to Fairfield, US 201 to Skowhegan, "
                        + "US 2 to Norridgewock, SA 148 to Canaan, SR 43 to Pittsfield");
    }

    @Test
    void aOneWaySegmentCannotBeDrivenBackwardsInAnItinerary() {
        Network network = roadLog();
        Segment ramp = network.segment("I95-2");
        network.addSegment(new Segment(
                "RAMP-1", ramp.route(), "pittsfield", "newport", new Miles(120), ramp.jurisdiction(), true));
        assertThat(new Itinerary(network, "pittsfield", List.of("RAMP-1")).destination())
                .isEqualTo("newport");
        assertThatThrownBy(() -> new Itinerary(network, "newport", List.of("RAMP-1")))
                .isInstanceOf(Impossible.class);
    }
}
