package io.github.rkaushikdev.jeepdolly.permits;

import static io.github.rkaushikdev.jeepdolly.support.Applications.anApplication;
import static io.github.rkaushikdev.jeepdolly.support.Applications.application;
import static io.github.rkaushikdev.jeepdolly.support.Offices.anOffice;
import static io.github.rkaushikdev.jeepdolly.support.Offices.office;
import static io.github.rkaushikdev.jeepdolly.support.RoadLog.roadLog;
import static io.github.rkaushikdev.jeepdolly.support.Streams.tractorLowboy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import io.github.rkaushikdev.jeepdolly.domain.HighwayClass;
import io.github.rkaushikdev.jeepdolly.enforcement.Account;
import io.github.rkaushikdev.jeepdolly.enforcement.EnforcementBook;
import io.github.rkaushikdev.jeepdolly.enforcement.Owed;
import io.github.rkaushikdev.jeepdolly.enforcement.Standing;
import io.github.rkaushikdev.jeepdolly.limits.WeightReading;
import io.github.rkaushikdev.jeepdolly.routing.Itinerary;
import io.github.rkaushikdev.jeepdolly.routing.RouteReading;
import io.github.rkaushikdev.jeepdolly.support.Carriers;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The three steps of the procedure somebody reads on their own. */
class AssessStagesTest {

    private static final LocalDate APRIL = LocalDate.of(2025, 4, 8);
    private static final List<String> ACROSS = List.of("US201-1", "US201-2", "US2-1", "SA148-1", "SR43-1");
    private static final Pounds GROSS = new Pounds(94000);

    private static Itinerary trip() {
        return trip(ACROSS, "waterville");
    }

    private static Itinerary trip(List<String> idents) {
        return trip(idents, "waterville");
    }

    private static Itinerary trip(List<String> idents, String origin) {
        return new Itinerary(roadLog(), origin, idents);
    }

    private static EnforcementBook heldBook() {
        return heldBook(Carriers.BICKFORD, LocalDate.of(2025, 1, 20));
    }

    private static EnforcementBook heldBook(int usdot, LocalDate written) {
        EnforcementBook book = new EnforcementBook();
        book.add(new Account(
                usdot, List.of(new Owed("E-25-0100", written, Dollars.of(500), Standing.dueDay(written, 21)))));
        return book;
    }

    @Test
    void aCarrierInGoodStandingIsEntitledToTheAfternoon() {
        assertThat(Assessor.entitled(anOffice(), anApplication(), APRIL, GROSS)).isEmpty();
    }

    @Test
    void aHeldCarrierIsRefusedBeforeAnythingIsMeasured() {
        Office desk = office().enforcement(heldBook()).build();
        List<String> found = Assessor.entitled(desk, anApplication(), APRIL, GROSS);
        assertThat(found).hasSize(1);
        assertThat(found.get(0)).contains("E-25-0100");
    }

    @Test
    void aCarrierWithNoPaperIsRefusedOnItsOwnStanding() {
        Application application = application().usdot(Carriers.AROOSTOOK).build();
        assertThat(Assessor.entitled(anOffice(), application, APRIL, GROSS)).isNotEmpty();
    }

    @Test
    void bothRefusalsComeBackTogetherRatherThanTheFirstOne() {
        Application application = application().usdot(Carriers.AROOSTOOK).build();
        Office desk = office().enforcement(heldBook(Carriers.AROOSTOOK, LocalDate.of(2025, 1, 20)))
                .build();
        assertThat(Assessor.entitled(desk, application, APRIL, GROSS)).hasSize(2);
    }

    @Test
    void theGoverningClassIsTheLeastGenerousRoadOnTheRoute() {
        Office desk = anOffice();
        assertThat(Assessor.governingClass(trip(), desk.statutes(), APRIL)).isSameAs(HighwayClass.STATE_AID);
    }

    @Test
    void aRouteOverNothingPermittableHasNoGoverningClass() {
        Office desk = anOffice();
        assertThatThrownBy(() -> Assessor.governingClass(trip(List.of("TR104-1"), "madison"), desk.statutes(), APRIL))
                .isInstanceOf(Impossible.class);
    }

    @Test
    void theLoadIsWeighedAgainstTheTightestTableTheRouteTouches() {
        WeightReading found = Assessor.weigh(anOffice(), trip(), tractorLowboy(), APRIL);
        assertThat(found.gross().value()).isEqualTo(94000);
        assertThat(found.overweight()).isTrue();
    }

    @Test
    void theInterstateIsTheTightestRoadOnGrossBecauseOfTheFederalCap() {
        WeightReading state = Assessor.weigh(anOffice(), trip(), tractorLowboy(), APRIL);
        WeightReading federal = Assessor.weigh(anOffice(), trip(List.of("I95-1", "I95-2")), tractorLowboy(), APRIL);
        assertThat(federal.grossLimit()).isLessThan(state.grossLimit());
    }

    @Test
    void oneMileOfInterstateHoldsTheWholeTripToTheFederalCap() {
        Itinerary mixed = trip(List.of("I95-1", "US201-2", "SA148-2", "SR43-1"), "waterville");
        assertThat(Assessor.weigh(anOffice(), mixed, tractorLowboy(), APRIL).grossLimit())
                .isEqualTo(new Pounds(80000));
    }

    @Test
    void walkingAClearRouteFindsNothingInTheWay() {
        RouteReading reading =
                Assessor.walkRoute(anOffice(), trip(), Envelope.read("12'", "13'6", "82'"), GROSS, APRIL);
        assertThat(reading.clear()).isTrue();
    }

    @Test
    void walkingItWithALoadThatWillNotTurnFindsTheCorner() {
        Envelope longOne = Envelope.read("12'", "13'6", "150'");
        RouteReading reading = Assessor.walkRoute(anOffice(), trip(), longOne, GROSS, APRIL);
        assertThat(reading.clear()).isFalse();
        assertThat(reading.fatal()).anyMatch(one -> one.kind().equals("corner"));
    }

    @Test
    void anAssessmentThatStoppedEarlyCarriesOnlyWhatItHad() {
        Assessment found = new Assessment(anApplication(), List.of("no route"));
        assertThat(found.permittable()).isFalse();
        assertThat(found.itinerary()).isEmpty();
        assertThat(found.conditions()).isEmpty();
        assertThat(found.quote()).isEmpty();
    }
}
