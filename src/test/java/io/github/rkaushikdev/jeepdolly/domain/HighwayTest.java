package io.github.rkaushikdev.jeepdolly.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Unreadable;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Route designations, the class they imply, and who maintains the road. */
class HighwayTest {

    @Test
    void aRouteReadsTheSameHoweverTheClerkTypedIt() {
        for (String written : List.of("I-95", "I 95", "i95", " I-95 ")) {
            assertThat(Route.parse(written)).as(written).isEqualTo(new Route("I", 95));
        }
        for (String written : List.of("US 2", "us-2", "US2")) {
            assertThat(Route.parse(written)).as(written).isEqualTo(new Route("US", 2));
        }
    }

    @Test
    void aRouteKeepsALetterSuffixBecauseTheRoadLogDoes() {
        assertThat(Route.parse("SR-27A")).isEqualTo(new Route("SR", 27, "A"));
        assertThat(Route.parse("sr27a")).hasToString("SR 27A");
    }

    @Test
    void aRoutePrintsWithTheDashOnlyWhereTheSignsUseOne() {
        assertThat(Route.parse("I-95")).hasToString("I-95");
        assertThat(Route.parse("US 2")).hasToString("US 2");
        assertThat(Route.parse("TR 104")).hasToString("TR 104");
    }

    @Test
    void somethingThatIsNotARouteIsRefusedAndQuotedBack() {
        Unreadable refusal = catchThrowableOfType(Unreadable.class, () -> Route.parse("the old county road"));
        assertThat(refusal.facts().get("written")).isEqualTo("the old county road");
        assertThatThrownBy(() -> Route.parse("US 1234")).isInstanceOf(Unreadable.class);
        assertThatThrownBy(() -> Route.parse("XX 27")).isInstanceOf(Unreadable.class);
    }

    @Test
    void aRouteBuiltByHandIsCheckedAsHardAsAParsedOne() {
        assertThatThrownBy(() -> new Route("XX", 27)).isInstanceOf(Impossible.class);
        assertThatThrownBy(() -> new Route("US", 0)).isInstanceOf(Impossible.class);
        assertThatThrownBy(() -> new Route("US", 1000)).isInstanceOf(Impossible.class);
    }

    @Test
    void thePrefixDecidesTheClassOfRoad() {
        assertThat(Route.parse("I-95").highwayClass()).isSameAs(HighwayClass.INTERSTATE);
        assertThat(Route.parse("US 2").highwayClass()).isSameAs(HighwayClass.ARTERIAL);
        assertThat(Route.parse("SR 27").highwayClass()).isSameAs(HighwayClass.ARTERIAL);
        assertThat(Route.parse("SA 148").highwayClass()).isSameAs(HighwayClass.STATE_AID);
        assertThat(Route.parse("TR 104").highwayClass()).isSameAs(HighwayClass.TOWN_WAY);
    }

    @Test
    void theStateCannotWriteAPermitOverATownWay() {
        assertThat(HighwayClass.INTERSTATE.permittable()).isTrue();
        assertThat(HighwayClass.STATE_AID.permittable()).isTrue();
        assertThat(HighwayClass.TOWN_WAY.permittable()).isFalse();
        assertThat(HighwayClass.TOWN_WAY.stateMaintained()).isFalse();
    }

    @Test
    void onlyTheInterstateIsFederalAndItPrintsItsOwnName() {
        assertThat(HighwayClass.INTERSTATE.federal()).isTrue();
        assertThat(HighwayClass.ARTERIAL.federal()).isFalse();
        assertThat(HighwayClass.STATE_AID).hasToString("state aid");
    }

    @Test
    void routesSortByPrefixThenNumberForAReport() {
        List<Route> routes =
                Stream.of("US 2", "I-95", "SR 27", "SR 11").map(Route::parse).toList();
        assertThat(routes.stream().sorted().map(Route::toString)).containsExactly("I-95", "SR 11", "SR 27", "US 2");
    }

    @Test
    void aPlaceSaysWhereToAskAndWhichSeasonalOrderReachesIt() {
        Jurisdiction place = new Jurisdiction("Somerset", "Skowhegan", 3);
        assertThat(place.region()).isEqualTo("district 3");
        assertThat(place).hasToString("Skowhegan, Somerset county (district 3)");
    }

    @Test
    void aDistrictTheStateDoesNotHaveIsRefused() {
        assertThatThrownBy(() -> new Jurisdiction("Somerset", "Skowhegan", 0)).isInstanceOf(Impossible.class);
        assertThatThrownBy(() -> new Jurisdiction("Somerset", "Skowhegan", 12)).isInstanceOf(Impossible.class);
    }

    @Test
    void aPlaceWithNoTownIsNotAPlace() {
        assertThatThrownBy(() -> new Jurisdiction("Somerset", "  ", 3)).isInstanceOf(Impossible.class);
        assertThatThrownBy(() -> new Jurisdiction("", "Skowhegan", 3)).isInstanceOf(Impossible.class);
    }
}
