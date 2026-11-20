package io.github.rkaushikdev.jeepdolly.services;

import static io.github.rkaushikdev.jeepdolly.services.Notices.LINE_LENGTH;
import static io.github.rkaushikdev.jeepdolly.support.Applications.anApplication;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.permits.Permit;
import io.github.rkaushikdev.jeepdolly.support.Permits;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The letters, and the rule that a refusal letter says everything. */
class NoticesTest {

    private static final LocalDate APRIL = LocalDate.of(2025, 4, 7);

    @Test
    void aSentenceIsBrokenAtSpacesAndNeverMidWord() {
        List<String> lines = Notices.wrap("the load is nine inches too wide for the Kennebec River bridge", 20);
        assertThat(lines).allSatisfy(one -> assertThat(one.length()).isLessThanOrEqualTo(20));
        assertThat(String.join(" ", lines)).isEqualTo("the load is nine inches too wide for the Kennebec River bridge");
    }

    @Test
    void aWordLongerThanTheLineIsLeftAloneRatherThanCut() {
        String longWord = "supercalifragilisticexpialidocious";
        assertThat(Notices.wrap(longWord, 20)).containsExactly(longWord);
    }

    @Test
    void aLetterNarrowerThanAnybodyWritesIsRefused() {
        assertThatThrownBy(() -> Notices.wrap("something", 19)).isInstanceOf(Impossible.class);
    }

    @Test
    void aRefusalLetterSaysEveryReasonInTheOrderTheyWereFound() {
        Notice letter = Notices.refusalLetter(
                anApplication(),
                List.of("the Carrabassett bridge is posted at 48,000 lb", "US 2 is closed for culvert work"),
                APRIL);
        String text = letter.text();
        assertThat(text).contains(" 1. the Carrabassett bridge is posted");
        assertThat(text).contains(" 2. US 2 is closed");
        assertThat(text.indexOf("Carrabassett")).isLessThan(text.indexOf("culvert"));
    }

    @Test
    void aRefusalLetterWithNoReasonsIsAMistake() {
        assertThatThrownBy(() -> Notices.refusalLetter(anApplication(), List.of(), APRIL))
                .isInstanceOf(Impossible.class);
    }

    @Test
    void aRefusalLetterNamesTheTripItIsAbout() {
        Notice letter = Notices.refusalLetter(anApplication(), List.of("the load is too wide"), APRIL);
        assertThat(letter.text()).contains("Waterville to Pittsfield");
        assertThat(letter.about()).isEqualTo("application 25-04471");
        assertThat(letter.to()).isEqualTo("Dana Bickford");
    }

    @Test
    void everyLetterCarriesTheOfficeTheDayAndANumberToRing() {
        Notice letter = Notices.refusalLetter(anApplication(), List.of("the load is too wide"), APRIL);
        List<String> lines = letter.lines();
        assertThat(lines.get(0)).isEqualTo("Motor Carrier Permit Office");
        assertThat(lines.get(1)).isEqualTo("2025-04-07");
        assertThat(lines.get(lines.size() - 1)).endsWith("207 555 0100.");
    }

    @Test
    void aLetterIsNarrowEnoughToSurviveAFax() {
        Notice letter = Notices.refusalLetter(
                anApplication(),
                List.of("the Kennebec River bridge will not take the declared tridem at that spacing"),
                APRIL);
        assertThat(letter.widest()).isLessThanOrEqualTo(LINE_LENGTH + 4);
    }

    @Test
    void theLetterThatGoesWithAPermitCarriesTheFaceAndTheConditions() {
        Permit permit = Permits.aPermit(anApplication());
        String text = Notices.issueLetter(permit, APRIL).text();
        assertThat(text).contains("25-P-004471");
        assertThat(text).contains("94,000 lb gross");
        assertThat(text).contains("Conditions:");
        assertThat(text).contains("- Route as written.");
    }

    @Test
    void anExpiryNoticeListsThePermitsAndSaysTheyCannotBeExtended() {
        Permit permit = Permits.aPermit(anApplication());
        String text = Notices.expiryNotice(List.of(permit), "Bickford Heavy Haul", APRIL)
                .text();
        assertThat(text).contains("25-P-004471  expires 2025-04-10");
        assertThat(text).contains("cannot be extended");
    }

    @Test
    void anExpiryNoticeAboutNothingIsAMistake() {
        assertThatThrownBy(() -> Notices.expiryNotice(List.of(), "Bickford Heavy Haul", APRIL))
                .isInstanceOf(Impossible.class);
    }

    @Test
    void aNoticeSaysWhatItIsWhenItIsPrinted() {
        Permit permit = Permits.aPermit(anApplication());
        assertThat(Notices.issueLetter(permit, APRIL)).hasToString("permit 25-P-004471 to Bickford Heavy Haul");
    }
}
