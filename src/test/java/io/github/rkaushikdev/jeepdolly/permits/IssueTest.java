package io.github.rkaushikdev.jeepdolly.permits;

import static io.github.rkaushikdev.jeepdolly.support.Applications.application;
import static io.github.rkaushikdev.jeepdolly.support.Offices.anOffice;
import static io.github.rkaushikdev.jeepdolly.support.Offices.office;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.NotAllowed;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.SeasonBook;
import io.github.rkaushikdev.jeepdolly.SeasonKind;
import io.github.rkaushikdev.jeepdolly.SeasonOrder;
import io.github.rkaushikdev.jeepdolly.conditions.Condition;
import io.github.rkaushikdev.jeepdolly.domain.AxleStream;
import io.github.rkaushikdev.jeepdolly.domain.Combination;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import io.github.rkaushikdev.jeepdolly.domain.HighwayClass;
import io.github.rkaushikdev.jeepdolly.domain.Restriction;
import io.github.rkaushikdev.jeepdolly.domain.RestrictionBook;
import io.github.rkaushikdev.jeepdolly.domain.RestrictionKind;
import io.github.rkaushikdev.jeepdolly.limits.PermitKind;
import io.github.rkaushikdev.jeepdolly.routing.Itinerary;
import io.github.rkaushikdev.jeepdolly.support.Carriers;
import io.github.rkaushikdev.jeepdolly.support.Combinations;
import io.github.rkaushikdev.jeepdolly.support.Streams;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The whole procedure on one application, and the permit at the end of it. */
class IssueTest {

    private static final LocalDate APRIL = LocalDate.of(2025, 4, 8);
    private static final LocalDate FILED = LocalDate.of(2025, 4, 7);
    private static final List<String> WRITTEN = List.of("US201-1", "US201-2", "SA148-2", "SR43-1");

    private static Application.Builder move() {
        return application();
    }

    private static Application written() {
        return move().route(WRITTEN).build();
    }

    private static List<String> codes(Assessment found) {
        return found.conditions().stream().map(Condition::code).toList();
    }

    @Test
    void anOrdinaryMoveComesBackPermittableWithAPriceOnIt() {
        Assessment found = Assessor.assess(anOffice(), move().build());
        assertThat(found.permittable()).isTrue();
        assertThat(found.kind()).isSameAs(PermitKind.ORDINARY);
        assertThat(found.itinerary()).isPresent();
        assertThat(found.quote()).isPresent();
        assertThat(found.quote().orElseThrow().total().cents()).isPositive();
        assertThat(found).hasToString("25-04471: ordinary permit");
    }

    @Test
    void theRouteIsTheOneTheHaulerWroteWhenHeWroteOne() {
        Assessment found = Assessor.assess(anOffice(), written());
        assertThat(found.itinerary()).isPresent();
        assertThat(found.itinerary().orElseThrow().idents()).containsExactlyElementsOf(WRITTEN);
    }

    @Test
    void theOfficeFindsARouteWhenTheHaulerLeftItBlank() {
        Itinerary itinerary =
                Assessor.assess(anOffice(), move().build()).itinerary().orElseThrow();
        assertThat(itinerary.origin()).isEqualTo("waterville");
        assertThat(itinerary.destination()).isEqualTo("pittsfield");
    }

    @Test
    void aSuspendedCarrierIsRefusedBeforeAnythingIsMeasured() {
        Office desk = anOffice();
        desk.carriers().get(Carriers.BICKFORD).suspend("unpaid permits", LocalDate.of(2025, 1, 5));
        Assessment found = Assessor.assess(desk, move().build());
        assertThat(found.permittable()).isFalse();
        assertThat(found.refusals().get(0)).contains("suspended");
    }

    @Test
    void aCarrierWithNoPaperIsRefusedAndTheRestStillRuns() {
        Assessment found =
                Assessor.assess(anOffice(), move().usdot(Carriers.AROOSTOOK).build());
        assertThat(found.permittable()).isFalse();
        assertThat(found.refusals().get(0)).isEqualTo("no certificate of insurance on file");
        assertThat(found.itinerary()).isPresent();
    }

    @Test
    void aDivisibleLoadIsRefusedByTheClassification() {
        Assessment found = Assessor.assess(anOffice(), move().commodity("GRVL").build());
        assertThat(found.permittable()).isFalse();
        assertThat(found.refusals().get(0)).contains("could have been loaded to legal weight");
    }

    @Test
    void aLegalLoadIsToldItNeedsNoPermitAtAll() {
        Combination combination = Combinations.aCombination(Streams.legalSemi(), Envelope.read("8'", "13'", "70'"));
        Assessment found =
                Assessor.assess(anOffice(), move().combination(combination).build());
        assertThat(found.permittable()).isFalse();
        assertThat(found.refusals()).containsExactly("this load is legal and needs no permit");
        assertThat(found.kind()).isSameAs(PermitKind.NONE);
    }

    @Test
    void theGoverningTableIsTheTightestClassTheRouteTouches() {
        Assessment found = Assessor.assess(anOffice(), written());
        assertThat(found.itinerary()).isPresent();
        Office desk = anOffice();
        HighwayClass governing = Assessor.governingClass(found.itinerary().orElseThrow(), desk.statutes(), APRIL);
        assertThat(governing.value()).isEqualTo("state aid");
    }

    @Test
    void aClosedRoadOnTheWrittenRouteRefusesAndNamesIt() {
        RestrictionBook restrictions = new RestrictionBook();
        restrictions.add(new Restriction("R-1", "SA148-2", RestrictionKind.CLOSURE, "bridge out"));
        Assessment found = Assessor.assess(office().restrictions(restrictions).build(), written());
        assertThat(found.permittable()).isFalse();
        assertThat(found.refusals().get(0)).contains("SA148-2 closure");
    }

    @Test
    void aPostedStructureOnTheRouteRefusesTheWholeTrip() {
        List<String> across = List.of("US201-1", "US201-2", "US2-1", "SA148-1", "SR43-1");
        Assessment found = Assessor.assess(anOffice(), move().route(across).build());
        assertThat(found.permittable()).isFalse();
        assertThat(found.refusals()).contains("Carrabassett Stream will not take this load");
    }

    @Test
    void aThawOrderShowsUpAsAConditionRatherThanARefusal() {
        SeasonBook seasons = new SeasonBook();
        seasons.file(new SeasonOrder("25-04", "district 4", SeasonKind.THAW, APRIL, LocalDate.of(2025, 5, 2), 65));
        RestrictionBook restrictions = new RestrictionBook();
        restrictions.add(Restriction.builder("R-2", "SA148-2", RestrictionKind.SEASONAL, "spring")
                .region("district 4")
                .build());
        Office desk = office().restrictions(restrictions).seasons(seasons).build();
        Assessment found = Assessor.assess(desk, written());
        assertThat(found.permittable()).isTrue();
        assertThat(codes(found)).contains("R-02");
    }

    @Test
    void aWideLoadPicksUpEscortsMarkingAndDaylightTogether() {
        Combination combination =
                Combinations.aCombination(Streams.tractorLowboy(), Envelope.read("15'", "13'6", "82'", "0", "6'"));
        Assessment found = Assessor.assess(
                anOffice(), move().combination(combination).route(WRITTEN).build());
        assertThat(found.escorts())
                .hasValueSatisfying(escorts -> assertThat(escorts.front()).isEqualTo(1));
        assertThat(found.marking())
                .hasValueSatisfying(marking -> assertThat(marking.banners()).isEqualTo(2));
        assertThat(found.limits())
                .hasValueSatisfying(limits -> assertThat(limits.daylightOnly()).isTrue());
        assertThat(codes(found)).contains("T-02");
    }

    @Test
    void aSuperloadIsClassifiedAsOneAndPricedAsOne() {
        AxleStream stream =
                Streams.make(List.of(12000, 30000, 30000, 30000, 30000, 30000), List.of(200, 54, 240, 54, 54));
        Combination combination = Combinations.aCombination(stream, Envelope.read("12'", "13'6", "82'", "0", "6'"));
        Assessment found = Assessor.assess(
                anOffice(), move().combination(combination).commodity("XFMR").build());
        assertThat(found.kind()).isSameAs(PermitKind.SUPERLOAD);
        assertThat(found.quote()).isPresent();
        assertThat(found.quote().orElseThrow().line("engineering review")).isPresent();
    }

    @Test
    void issuingGivesANumberAndMovesTheApplicationOn() {
        Office desk = anOffice();
        Application subject = move().build();
        subject.submit(FILED);
        Assessment found = Assessor.assess(desk, subject);
        Permit permit = Issuer.issue(desk, found, FILED);
        assertThat(permit.number()).hasToString("25-P-000001");
        assertThat(permit.carrierName()).isEqualTo("Bickford Heavy Haul");
        assertThat(permit.gross()).isEqualTo(new Pounds(94000));
        assertThat(subject.state().value()).isEqualTo("issued");
        assertThat(permit.conditions()).isNotEmpty();
    }

    @Test
    void aRefusedAssessmentWillNotIssue() {
        Office desk = anOffice();
        Application subject = move().commodity("GRVL").build();
        Assessment found = Assessor.assess(desk, subject);
        NotAllowed refusal = catchThrowableOfType(NotAllowed.class, () -> Issuer.issue(desk, found, FILED));
        assertThat(refusal.facts().get("reasons")).isEqualTo(found.refusals());
    }

    @Test
    void aSuperloadTakesANumberOutOfItsOwnSeries() {
        Office desk = anOffice();
        Combination combination =
                Combinations.aCombination(Streams.tractorLowboy(), Envelope.read("18'", "13'6", "82'", "0", "6'"));
        Application subject = move().combination(combination).build();
        subject.submit(FILED);
        Assessment found = Assessor.assess(desk, subject);
        assertThat(found.kind()).isSameAs(PermitKind.SUPERLOAD);
        Permit permit = Issuer.issue(desk, found, FILED);
        assertThat(permit.number().toString()).startsWith("25-S-");
    }

    @Test
    void aLoadWithNowhereToGoReportsThatAndNothingElse() {
        RestrictionBook restrictions = new RestrictionBook();
        for (String ident : List.of("I95-2", "SR43-1")) {
            restrictions.add(new Restriction("R-" + ident, ident, RestrictionKind.CLOSURE, "bridge out"));
        }
        Assessment found = Assessor.assess(office().restrictions(restrictions).build(), move().build());
        assertThat(found.refusals()).containsExactly("no route this load can take on the days asked for");
        assertThat(found.itinerary()).isEmpty();
        assertThat(found.conditions()).isEmpty();
    }

    @Test
    void aLoadTooTallForTheOnlyRouteIsRefusedOnTheClearance() {
        RestrictionBook restrictions = new RestrictionBook();
        restrictions.add(Restriction.builder("R-9", "US201-2", RestrictionKind.CLEARANCE, "arch")
                .height(Inches.of(12))
                .build());
        Assessment found = Assessor.assess(office().restrictions(restrictions).build(), written());
        assertThat(found.permittable()).isFalse();
        assertThat(found.refusals().get(0)).contains("clearance");
    }
}
