package io.github.rkaushikdev.jeepdolly.permits;

import static io.github.rkaushikdev.jeepdolly.support.Applications.application;
import static io.github.rkaushikdev.jeepdolly.support.Offices.anOffice;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;

import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.NotAllowed;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.conditions.Condition;
import io.github.rkaushikdev.jeepdolly.domain.AxleStream;
import io.github.rkaushikdev.jeepdolly.domain.Combination;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import io.github.rkaushikdev.jeepdolly.domain.Restriction;
import io.github.rkaushikdev.jeepdolly.domain.RestrictionKind;
import io.github.rkaushikdev.jeepdolly.support.Combinations;
import io.github.rkaushikdev.jeepdolly.support.Streams;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Amending a permit, which replaces it rather than editing it. */
class AmendTest {

    private static final LocalDate FILED = LocalDate.of(2025, 4, 7);
    private static final LocalDate LATER = LocalDate.of(2025, 4, 14);
    private static final List<String> ACROSS = List.of("US201-1", "US201-2", "SA148-2", "SR43-1");

    /** The excavator move over the written route, submitted, assessed and issued. */
    private static Permit issued(Office office) {
        Application subject = application().route(ACROSS).build();
        subject.submit(FILED);
        return Issuer.issue(office, Assessor.assess(office, subject), FILED);
    }

    /** The revised application, filed under its own reference over the same route. */
    private static Application.Builder revised() {
        return application("25-04471-A").route(ACROSS);
    }

    @Test
    void nothingChangedIsNotAnAmendment() {
        Office office = anOffice();
        Permit permit = issued(office);
        assertThatThrownBy(() -> Amender.amend(office, permit, revised().build(), FILED))
                .isInstanceOf(Impossible.class);
    }

    @Test
    void theChangesAreWorkedOutAndNamed() {
        Office office = anOffice();
        Permit permit = issued(office);
        List<Change> changes =
                Amender.changesBetween(permit, revised().end(LATER).build());
        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).what()).isEqualTo("dates");
        assertThat(changes.get(0)).hasToString("dates: 2025-04-08 to 2025-04-10 to 2025-04-08 to 2025-04-14");
    }

    @Test
    void aChangeOfWeightIsNoticed() {
        Office office = anOffice();
        Permit permit = issued(office);
        AxleStream stream =
                Streams.make(List.of(12000, 17000, 17000, 18000, 18000, 18000), List.of(200, 54, 180, 52, 52));
        Combination heavier = Combinations.aCombination(stream);
        List<Change> changes =
                Amender.changesBetween(permit, revised().combination(heavier).build());
        assertThat(changes).extracting(Change::what).containsExactly("gross");
        assertThat(changes.get(0).after()).isEqualTo("100,000 lb");
    }

    @Test
    void anAmendmentIssuesAReplacementAndSupersedesTheOldPermit() {
        Office office = anOffice();
        Permit permit = issued(office);
        Amendment found = Amender.amend(office, permit, revised().end(LATER).build(), FILED);
        assertThat(permit.state()).isSameAs(PermitState.SUPERSEDED);
        assertThat(permit.supersededBy()).contains(found.replacement().number());
        assertThat(found.replacement().supersedes()).contains(permit.number());
        assertThat(found.replacement().number().toString()).startsWith("25-R-");
    }

    @Test
    void theReplacementCarriesTheNewDatesAndItsOwnConditions() {
        Office office = anOffice();
        Permit permit = issued(office);
        Amendment found = Amender.amend(office, permit, revised().end(LATER).build(), FILED);
        assertThat(found.replacement().end()).isEqualTo(LATER);
        assertThat(found.replacement().days()).isEqualTo(7);
        assertThat(found.replacement().conditions()).isNotEmpty();
    }

    @Test
    void theAmendmentCostsTheFlatFeeWhenNothingGotDearer() {
        Office office = anOffice();
        Permit permit = issued(office);
        Amendment found = Amender.amend(office, permit, revised().end(LATER).build(), FILED);
        assertThat(found.quote().total()).isEqualTo(Dollars.of(25));
    }

    @Test
    void anAmendmentThatAddsWeightChargesTheDifferenceToo() {
        Office office = anOffice();
        Permit permit = issued(office);
        AxleStream stream =
                Streams.make(List.of(12000, 20000, 20000, 20000, 20000, 20000), List.of(200, 54, 240, 54, 54));
        Combination heavier = Combinations.aCombination(stream);
        Amendment found =
                Amender.amend(office, permit, revised().combination(heavier).build(), FILED);
        assertThat(found.quote().total()).isGreaterThan(Dollars.of(25));
        assertThat(found.quote().line("difference in permit fee")).isPresent();
    }

    @Test
    void anAmendmentIsAssessedAgainstTodaysRoadAndCanBeRefused() {
        Office office = anOffice();
        Permit permit = issued(office);
        office.restrictions().add(new Restriction("R-1", "SA148-2", RestrictionKind.CLOSURE, "bridge out"));
        NotAllowed refusal = catchThrowableOfType(
                NotAllowed.class,
                () -> Amender.amend(office, permit, revised().end(LATER).build(), FILED));
        assertThat(refusal.facts().get("reasons")).asInstanceOf(LIST).isNotEmpty();
        assertThat(permit.state()).isSameAs(PermitState.ISSUED);
    }

    @Test
    void aSupersededPermitCannotBeAmendedAgain() {
        Office office = anOffice();
        Permit permit = issued(office);
        Amender.amend(office, permit, revised().end(LATER).build(), FILED);
        assertThatThrownBy(() -> Amender.amend(
                        office, permit, revised().end(LocalDate.of(2025, 4, 15)).build(), FILED))
                .isInstanceOf(NotAllowed.class);
    }

    @Test
    void anExpiredPermitCannotBeAmended() {
        Office office = anOffice();
        Permit permit = issued(office);
        assertThatThrownBy(
                        () -> Amender.amend(office, permit, revised().end(LATER).build(), LocalDate.of(2025, 5, 1)))
                .isInstanceOf(NotAllowed.class)
                .hasToString("not_allowed: " + permit.number() + " expired on 2025-04-10");
    }

    @Test
    void theAmendmentPrintsWhatMoved() {
        Office office = anOffice();
        Permit permit = issued(office);
        Amendment found = Amender.amend(office, permit, revised().end(LATER).build(), FILED);
        assertThat(found.toString()).endsWith("dates changed");
        assertThat(found.assessment().permittable()).isTrue();
    }

    @Test
    void aChangeOfDestinationIsAnAmendmentLikeAnyOther() {
        Office office = anOffice();
        Permit permit = issued(office);
        List<String> onward = new ArrayList<>(ACROSS);
        onward.add("US2-2");
        List<Change> changes = Amender.changesBetween(
                permit, revised().destination("newport").route(onward).build());
        assertThat(changes).extracting(Change::what).containsExactly("trip", "route");
    }

    @Test
    void aWiderLoadOnTheAmendmentPicksUpTheEscortConditions() {
        Office office = anOffice();
        Permit permit = issued(office);
        Combination wide =
                Combinations.aCombination(Streams.tractorLowboy(), Envelope.read("15'", "13'6", "82'", "0", "6'"));
        Amendment found =
                Amender.amend(office, permit, revised().combination(wide).build(), FILED);
        assertThat(found.replacement().conditions()).extracting(Condition::code).contains("E-01");
        assertThat(found.replacement().gross()).isEqualTo(new Pounds(94000));
    }
}
