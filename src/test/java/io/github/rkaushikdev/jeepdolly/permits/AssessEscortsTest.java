package io.github.rkaushikdev.jeepdolly.permits;

import static io.github.rkaushikdev.jeepdolly.support.Applications.anApplication;
import static io.github.rkaushikdev.jeepdolly.support.Applications.application;
import static io.github.rkaushikdev.jeepdolly.support.Offices.anOffice;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.jeepdolly.conditions.Condition;
import io.github.rkaushikdev.jeepdolly.domain.Combination;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import io.github.rkaushikdev.jeepdolly.escorts.Naming;
import io.github.rkaushikdev.jeepdolly.support.Combinations;
import io.github.rkaushikdev.jeepdolly.support.Streams;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** What the assessment makes of the pilot cars a hauler names. */
class AssessEscortsTest {

    private static final LocalDate APRIL = LocalDate.of(2025, 4, 8);

    private static Combination wide() {
        return Combinations.aCombination(Streams.tractorLowboy(), Envelope.read("15'", "13'6", "82'", "0", "6'"));
    }

    private static Combination tall() {
        return Combinations.aCombination(Streams.tractorLowboy(), Envelope.read("15'", "15'", "82'", "0", "6'"));
    }

    private static List<String> codes(Assessment found) {
        return found.conditions().stream().map(Condition::code).toList();
    }

    private static Application wideNaming(String... cards) {
        return application().combination(wide()).escortCards(List.of(cards)).build();
    }

    @Test
    void aLoadNeedingEscortsWithNobodyNamedIsStillPermittable() {
        Assessment found =
                Assessor.assess(anOffice(), application().combination(wide()).build(), APRIL);
        assertThat(found.permittable()).isTrue();
        assertThat(found.naming()).isPresent();
        assertThat(found.naming().orElseThrow().complete()).isFalse();
        assertThat(codes(found)).contains("E-06");
        assertThat(codes(found)).doesNotContain("E-05");
    }

    @Test
    void theNamedPilotCarsPrintOnThePermit() {
        Assessment found = Assessor.assess(anOffice(), wideNaming("C-1001"), APRIL);
        assertThat(found.permittable()).isTrue();
        Condition printed = found.conditions().stream()
                .filter(one -> one.code().equals("E-05"))
                .findFirst()
                .orElseThrow();
        assertThat(printed.text()).isEqualTo("Pilot cars for this move: R Dubois (C-1001).");
    }

    @Test
    void namingEnoughCardsTakesTheReminderOffThePermit() {
        Assessment found = Assessor.assess(anOffice(), wideNaming("C-1001"), APRIL);
        assertThat(found.naming())
                .hasValueSatisfying(naming -> assertThat(naming.complete()).isTrue());
        assertThat(codes(found)).doesNotContain("E-06");
    }

    @Test
    void aCardThatHasRunOutRefusesTheApplication() {
        Assessment found = Assessor.assess(anOffice(), wideNaming("C-1003"), APRIL);
        assertThat(found.permittable()).isFalse();
        assertThat(found.refusals()).anyMatch(one -> one.contains("C-1003 is expired"));
    }

    @Test
    void aCardNobodyHasRefusesTheApplicationAndQuotesIt() {
        Assessment found = Assessor.assess(anOffice(), wideNaming("C-9999"), APRIL);
        assertThat(found.refusals()).contains("pilot car card C-9999 is not on the register");
    }

    @Test
    void aTallLoadWantsTheHeightPoleEndorsementOnSomebody() {
        Application plain =
                application().combination(tall()).escortCards(List.of("C-1001")).build();
        assertThat(Assessor.assess(anOffice(), plain, APRIL).permittable()).isFalse();
        Application poled =
                application().combination(tall()).escortCards(List.of("C-1002")).build();
        assertThat(Assessor.assess(anOffice(), poled, APRIL).permittable()).isTrue();
    }

    @Test
    void aLoadNeedingNoEscortsPrintsNothingAboutPilotCars() {
        Assessment found = Assessor.assess(anOffice(), anApplication(), APRIL);
        assertThat(found.escorts())
                .hasValueSatisfying(escorts -> assertThat(escorts.any()).isFalse());
        assertThat(codes(found)).doesNotContain("E-06");
        assertThat(codes(found)).doesNotContain("E-05");
    }

    @Test
    void theNamingSurvivesOntoTheAssessmentForTheLetter() {
        Assessment found = Assessor.assess(anOffice(), wideNaming("C-1001"), APRIL);
        assertThat(found.naming()).isPresent();
        assertThat(found.naming().map(Naming::toString)).contains("1 of 1 named");
    }

    @Test
    void anAssessmentThatNeverFoundARouteCarriesNoNaming() {
        Application nowhere =
                application().origin("madison").destination("madison").build();
        Assessment found = Assessor.assess(anOffice(), nowhere, APRIL);
        assertThat(found.permittable()).isFalse();
        assertThat(found.naming()).isEmpty();
    }
}
