package io.github.rkaushikdev.jeepdolly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.enforcement.Standing;
import io.github.rkaushikdev.jeepdolly.permits.Reprint;
import io.github.rkaushikdev.jeepdolly.reports.LineSurvey;
import io.github.rkaushikdev.jeepdolly.services.Renewals;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Settings, read once from the environment, with defaults that are safe. */
class ConfigTest {

    private static Settings read(Map<String, String> environment) {
        return Settings.fromEnvironment(environment);
    }

    @Test
    void theDefaultsAreTheOnesThatAreSafeToBeWrong() {
        Settings settings = read(Map.of());
        assertThat(settings.databaseUrl()).isEqualTo(Settings.DEFAULT_URL);
        assertThat(settings.databaseUrl()).startsWith("jdbc:h2:file:");
        assertThat(settings.minimumMiles()).isEqualTo(Settings.DEFAULT_MINIMUM_MILES);
        assertThat(settings.echoSql()).isFalse();
    }

    @Test
    void theEnvironmentIsReadWithOnePrefix() {
        Settings settings = read(Map.of(
                Settings.PREFIX + "OFFICE_NAME",
                "Northern District Permit Office",
                Settings.PREFIX + "MINIMUM_MILES",
                "25"));
        assertThat(settings.officeName()).isEqualTo("Northern District Permit Office");
        assertThat(settings.minimumMiles()).isEqualTo(25);
    }

    @Test
    void aFlagIsReadFromTheWordsPeopleActuallyWrite() {
        for (String written : List.of("yes", "true", "ON", "1")) {
            assertThat(read(Map.of(Settings.PREFIX + "ECHO_SQL", written)).echoSql())
                    .as(written)
                    .isTrue();
        }
        for (String written : List.of("no", "false", "OFF", "0")) {
            assertThat(read(Map.of(Settings.PREFIX + "ECHO_SQL", written)).echoSql())
                    .as(written)
                    .isFalse();
        }
    }

    @Test
    void aFlagThatIsNeitherIsRefusedRatherThanGuessed() {
        assertThatThrownBy(() -> read(Map.of(Settings.PREFIX + "ECHO_SQL", "maybe")))
                .isInstanceOf(Impossible.class)
                .hasMessageContaining("yes or no");
    }

    @Test
    void aNumberThatIsNotOneIsRefused() {
        assertThatThrownBy(() -> read(Map.of(Settings.PREFIX + "MINIMUM_MILES", "ten")))
                .isInstanceOf(Impossible.class);
    }

    @Test
    void aSettingThatCouldNotHaveBeenMeantIsRefused() {
        assertThatThrownBy(() -> Settings.defaults().withDatabase("   ")).isInstanceOf(Impossible.class);
        assertThatThrownBy(() -> Settings.defaults().withMinimumMiles(-1)).isInstanceOf(Impossible.class);
        assertThatThrownBy(() -> Settings.defaults().withReviewDays(0)).isInstanceOf(Impossible.class);
    }

    @Test
    void thePasswordNeverReachesTheLogLine() {
        Settings settings =
                Settings.defaults().withDatabase("jdbc:postgresql://permits:hunter2@db.internal:5432/jeepdolly");
        assertThat(settings.safeUrl()).doesNotContain("hunter2");
        assertThat(settings.safeUrl()).endsWith("@db.internal:5432/jeepdolly");
        assertThat(settings.toString()).contains("permits:***");
    }

    @Test
    void aUrlWithNoPasswordIsLeftAlone() {
        Settings settings = Settings.defaults().withDatabase("jdbc:h2:file:./jeepdolly");
        assertThat(settings.safeUrl()).isEqualTo(settings.databaseUrl());
        assertThat(settings.inMemory()).isFalse();
    }

    @Test
    void anInMemoryDatabaseSaysSoBecauseItChangesWhatStartsUp() {
        assertThat(Settings.defaults().withDatabase("jdbc:h2:mem:jeepdolly").inMemory())
                .isTrue();
    }

    @Test
    void theSettingsPrintTheOfficeAndTheDatabaseItIsOn() {
        Settings settings = Settings.defaults().withOfficeName("Motor Carrier Permit Office");
        assertThat(settings.toString()).startsWith("Motor Carrier Permit Office on jdbc:h2:file:");
    }

    @Test
    void theOfficeRulesDefaultToTheConstantsThatOwnThem() {
        OfficeRules rules = OfficeRules.defaults();
        assertThat(rules.freeCopies()).isEqualTo(Reprint.FREELY);
        assertThat(rules.renewalDays()).isEqualTo(Renewals.AHEAD_DAYS);
        assertThat(rules.citationGrace()).isEqualTo(Standing.GRACE_DAYS);
        assertThat(rules.lineSurveyYears()).isEqualTo(LineSurvey.CYCLE_YEARS);
    }

    @Test
    void aSettingsObjectCarriesTheRulesWithoutBeingTold() {
        assertThat(Settings.defaults().rules()).isEqualTo(OfficeRules.defaults());
    }

    @Test
    void anOfficeThatPrintsNoCopiesFreelyIsRefused() {
        assertThatThrownBy(() -> OfficeRules.defaults().withFreeCopies(0)).isInstanceOf(Impossible.class);
    }

    @Test
    void aRenewalWindowOfNoDaysIsRefused() {
        assertThatThrownBy(() -> OfficeRules.defaults().withRenewalDays(0)).isInstanceOf(Impossible.class);
    }

    @Test
    void aNegativeGraceIsRefused() {
        assertThatThrownBy(() -> OfficeRules.defaults().withCitationGrace(-1)).isInstanceOf(Impossible.class);
    }

    @Test
    void aSurveyCycleOfNoYearsIsRefused() {
        assertThatThrownBy(() -> OfficeRules.defaults().withLineSurveyYears(0)).isInstanceOf(Impossible.class);
    }

    @Test
    void aGraceOfNoDaysIsAllowedBecauseSomeStatesHaveNone() {
        assertThat(OfficeRules.defaults().withCitationGrace(0).citationGrace()).isZero();
    }

    @Test
    void theRulesPrintForTheSettingsPage() {
        List<String> printed = OfficeRules.defaults().withFreeCopies(5).lines();
        assertThat(printed.get(0)).isEqualTo("free copies       5");
        assertThat(printed).hasSize(4);
    }

    @Test
    void theRulesSayTheShortFormToo() {
        assertThat(OfficeRules.defaults()).hasToString("3 copies, 30 day renewal window");
    }

    @Test
    void theRulesComeOffTheEnvironment() {
        OfficeRules found = read(Map.of("JEEPDOLLY_FREE_COPIES", "5", "JEEPDOLLY_CITATION_GRACE", "14"))
                .rules();
        assertThat(found.freeCopies()).isEqualTo(5);
        assertThat(found.citationGrace()).isEqualTo(14);
    }

    @Test
    void aRuleThatIsNotANumberIsRefused() {
        assertThatThrownBy(() -> read(Map.of("JEEPDOLLY_FREE_COPIES", "several")))
                .isInstanceOf(Impossible.class);
    }
}
