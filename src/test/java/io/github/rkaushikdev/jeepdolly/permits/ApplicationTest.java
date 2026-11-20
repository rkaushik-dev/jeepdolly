package io.github.rkaushikdev.jeepdolly.permits;

import static io.github.rkaushikdev.jeepdolly.support.Applications.anApplication;
import static io.github.rkaushikdev.jeepdolly.support.Applications.application;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.NotAllowed;
import io.github.rkaushikdev.jeepdolly.Pounds;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The application, what makes it incomplete, and the states it moves through. */
class ApplicationTest {

    private static final LocalDate FILED = LocalDate.of(2025, 4, 7);
    private static final LocalDate START = LocalDate.of(2025, 4, 8);

    private static Application.Entry last(Application application) {
        List<Application.Entry> history = application.history();
        return history.get(history.size() - 1);
    }

    @Test
    void aNewApplicationIsADraftWithOneLineOfHistory() {
        Application application = anApplication();
        assertThat(application.state()).isSameAs(ApplicationState.DRAFT);
        assertThat(application.state().open()).isTrue();
        assertThat(application.history())
                .containsExactly(new Application.Entry(FILED, ApplicationState.DRAFT, "typed"));
        assertThat(application).hasToString("25-04471 waterville to pittsfield draft");
    }

    @Test
    void theApplicationCarriesTheGrossOffTheCombination() {
        assertThat(anApplication().gross()).isEqualTo(new Pounds(94000));
        assertThat(anApplication().spanDays()).isEqualTo(3);
    }

    @Test
    void aCompleteApplicationHasNothingToSayAboutItself() {
        Application application = anApplication();
        assertThat(application.complete()).isTrue();
        assertThat(application.problems()).isEmpty();
    }

    @Test
    void aSpanThatEndsBeforeItStartsIsIncomplete() {
        Application application = application()
                .start(LocalDate.of(2025, 4, 10))
                .end(LocalDate.of(2025, 4, 8))
                .build();
        assertThat(application.complete()).isFalse();
        assertThat(application.problems()).containsExactly("the travel span ends before it starts");
    }

    @Test
    void aSpanLongerThanTheOfficeWritesIsIncomplete() {
        Application application = application().end(LocalDate.of(2025, 4, 30)).build();
        assertThat(application.problems().get(0)).contains("10 days at most");
    }

    @Test
    void travelBeforeTheApplicationWasFiledIsIncomplete() {
        Application application = application()
                .start(LocalDate.of(2025, 4, 1))
                .end(LocalDate.of(2025, 4, 3))
                .build();
        assertThat(application.problems().get(0)).contains("starts before the application was filed");
    }

    @Test
    void anEmptyTrailerIsIncomplete() {
        assertThat(application().pieces(List.of()).build().problems())
                .containsExactly("nothing is described on the trailer");
    }

    @Test
    void aTripToWhereItStartedIsIncomplete() {
        Application application = application().destination("waterville").build();
        assertThat(application.problems()).containsExactly("the trip starts and ends in the same place");
    }

    @Test
    void anApplicationWithNoReferenceIsNotAnApplication() {
        assertThatThrownBy(() -> anApplication("   ")).isInstanceOf(Impossible.class);
    }

    @Test
    void anApplicantNeedsANameAndANumberToRing() {
        assertThatThrownBy(() -> new Applicant("  ", "207 555 0143")).isInstanceOf(Impossible.class);
        assertThatThrownBy(() -> new Applicant("Dana Bickford", "")).isInstanceOf(Impossible.class);
        assertThat(new Applicant("Dana Bickford", "207 555 0143")).hasToString("Dana Bickford 207 555 0143");
    }

    @Test
    void submittingMovesItOnAndRecordsTheDay() {
        Application application = anApplication();
        application.submit(FILED);
        assertThat(application.state()).isSameAs(ApplicationState.SUBMITTED);
        assertThat(last(application)).isEqualTo(new Application.Entry(FILED, ApplicationState.SUBMITTED, "submitted"));
    }

    @Test
    void anIncompleteApplicationWillNotSubmitAndSaysTheFirstReason() {
        Application application = application().pieces(List.of()).build();
        Impossible refusal = catchThrowableOfType(Impossible.class, () -> application.submit(FILED));
        assertThat(refusal.facts().get("reference")).isEqualTo("25-04471");
        assertThat(application.state()).isSameAs(ApplicationState.DRAFT);
    }

    @Test
    void theSameApplicationDoesNotSubmitTwice() {
        Application application = anApplication();
        application.submit(FILED);
        assertThatThrownBy(() -> application.submit(FILED)).isInstanceOf(NotAllowed.class);
    }

    @Test
    void itCanGoToAnEngineerAndThenBeIssued() {
        Application application = anApplication();
        application.submit(FILED);
        application.sendForReview(FILED, "superload on width");
        assertThat(application.state()).isSameAs(ApplicationState.IN_REVIEW);
        application.issue(START, "25-004471");
        assertThat(application.state()).isSameAs(ApplicationState.ISSUED);
        assertThat(application.state().open()).isFalse();
        assertThat(last(application).note()).isEqualTo("issued as 25-004471");
    }

    @Test
    void aDraftCannotBeIssuedAndAReviewCannotBeAskedForTwice() {
        Application application = anApplication();
        assertThatThrownBy(() -> application.issue(START, "25-004471")).isInstanceOf(NotAllowed.class);
        application.submit(FILED);
        application.sendForReview(FILED);
        assertThatThrownBy(() -> application.sendForReview(FILED)).isInstanceOf(NotAllowed.class);
    }

    @Test
    void aRefusalRecordsItsReasonAndClosesTheFile() {
        Application application = anApplication();
        application.submit(FILED);
        application.refuse(START, "the Carrabassett bridge is posted at 48,000 lb");
        assertThat(application.state()).isSameAs(ApplicationState.REFUSED);
        assertThat(application.state().open()).isFalse();
        assertThatThrownBy(() -> application.withdraw(START)).isInstanceOf(NotAllowed.class);
    }

    @Test
    void aCarrierCanWithdrawAnythingStillOpen() {
        Application application = anApplication();
        application.submit(FILED);
        application.withdraw(START);
        assertThat(application.state()).isSameAs(ApplicationState.WITHDRAWN);
        assertThat(last(application).note()).isEqualTo("withdrawn by the carrier");
    }
}
