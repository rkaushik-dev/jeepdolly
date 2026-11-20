package io.github.rkaushikdev.jeepdolly.enforcement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.NotAllowed;
import io.github.rkaushikdev.jeepdolly.Pounds;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** A citation, and what becomes of it. */
class CitationTest {

    private static final PenaltySchedule SCHEDULE = PenaltySchedule.standard();
    private static final LocalDate WRITTEN = LocalDate.of(2025, 4, 8);
    private static final LocalDate FILED = LocalDate.of(2025, 4, 11);
    private static final LocalDate LATE = LocalDate.of(2025, 5, 15);

    /** An overweight citation twelve thousand pounds over, ready for a test to change. */
    private static Citation.Builder aCitation() {
        return aCitation(Offence.OVERWEIGHT, new Pounds(12000), 0);
    }

    private static Citation.Builder aCitation(Offence offence, Pounds over, int prior) {
        return Citation.builder()
                .number("E-25-0417")
                .written(WRITTEN)
                .filed(FILED)
                .officer("Sgt A Thibodeau")
                .usdot(1204471)
                .carrierName("Bickford Heavy Haul")
                .offence(offence)
                .place("US201-2 at Fairfield")
                .assessed(Penalty.assess(SCHEDULE, offence, Optional.ofNullable(over), prior))
                .over(offence.byWeight() ? over : null);
    }

    @Test
    void aCitationStartsWrittenAndSaysWhenItWasFiled() {
        Citation one = aCitation().build();
        assertThat(one.state()).isSameAs(CitationState.WRITTEN);
        assertThat(one.history()).containsExactly(new Citation.Entry(FILED, CitationState.WRITTEN, "filed"));
    }

    @Test
    void aCitationWithNoNumberIsRefused() {
        assertThatThrownBy(() -> aCitation().number("  ").build()).isInstanceOf(Impossible.class);
    }

    @Test
    void aCitationFiledBeforeItWasWrittenIsRefused() {
        assertThatThrownBy(() -> aCitation().filed(LocalDate.of(2025, 4, 1)).build())
                .isInstanceOf(Impossible.class);
    }

    @Test
    void anOverweightCitationThatDoesNotSayByHowMuchIsRefused() {
        assertThatThrownBy(() -> Citation.builder()
                        .number("E-25-0418")
                        .written(WRITTEN)
                        .filed(FILED)
                        .officer("Sgt A Thibodeau")
                        .usdot(1204471)
                        .carrierName("Bickford Heavy Haul")
                        .offence(Offence.OVERWEIGHT)
                        .place("US201-2")
                        .assessed(Penalty.assess(SCHEDULE, Offence.OVERWEIGHT, new Pounds(1000)))
                        .build())
                .isInstanceOf(Impossible.class);
    }

    @Test
    void theAmountComesOffTheAssessment() {
        assertThat(aCitation().build().amount()).isEqualTo(Dollars.of(580));
    }

    @Test
    void theContestWindowRunsFromTheDayItWasWritten() {
        Citation one = aCitation().build();
        assertThat(one.contestCloses()).isEqualTo(LocalDate.of(2025, 4, 29));
        assertThat(ChronoUnit.DAYS.between(WRITTEN, one.contestCloses())).isEqualTo(Citation.CONTEST_DAYS);
    }

    @Test
    void itIsContestableInsideTheWindowAndNotAfter() {
        Citation one = aCitation().build();
        assertThat(one.contestableOn(LocalDate.of(2025, 4, 29))).isTrue();
        assertThat(one.contestableOn(LocalDate.of(2025, 4, 30))).isFalse();
    }

    @Test
    void contestingItInsideTheWindowMovesIt() {
        Citation one = aCitation().build();
        one.contest(LocalDate.of(2025, 4, 20), "the scale was not certified");
        assertThat(one.state()).isSameAs(CitationState.CONTESTED);
        assertThat(last(one.history()).note()).isEqualTo("the scale was not certified");
    }

    @Test
    void contestingItAfterTheWindowIsRefusedAndNamesTheDay() {
        Citation one = aCitation().build();
        assertThatThrownBy(() -> one.contest(LATE, "too late"))
                .isInstanceOf(NotAllowed.class)
                .hasMessageContaining("2025-04-29");
    }

    @Test
    void contestingItWithoutGroundsIsRefused() {
        Citation one = aCitation().build();
        assertThatThrownBy(() -> one.contest(LocalDate.of(2025, 4, 20), "   ")).isInstanceOf(Impossible.class);
    }

    @Test
    void aContestedCitationCanBeUpheldAndStillWantsPaying() {
        Citation one = aCitation().build();
        one.contest(LocalDate.of(2025, 4, 20), "the scale was not certified");
        one.uphold(LocalDate.of(2025, 5, 2));
        assertThat(one.state()).isSameAs(CitationState.UPHELD);
        assertThat(one.state().owing()).isTrue();
    }

    @Test
    void aContestedCitationCanBeDismissedAndThatIsTheEnd() {
        Citation one = aCitation().build();
        one.contest(LocalDate.of(2025, 4, 20), "the scale was not certified");
        one.dismiss(LocalDate.of(2025, 5, 2), "scale certificate had lapsed");
        assertThat(one.state().closed()).isTrue();
        assertThat(one.state().owing()).isFalse();
    }

    @Test
    void aDismissalSaysWhy() {
        Citation one = aCitation().build();
        one.contest(LocalDate.of(2025, 4, 20), "grounds");
        assertThatThrownBy(() -> one.dismiss(LocalDate.of(2025, 5, 2), "")).isInstanceOf(Impossible.class);
    }

    @Test
    void nothingIsUpheldOrDismissedBeforeItIsContested() {
        Citation one = aCitation().build();
        assertThatThrownBy(() -> one.uphold(LocalDate.of(2025, 5, 2))).isInstanceOf(NotAllowed.class);
        assertThatThrownBy(() -> one.dismiss(LocalDate.of(2025, 5, 2), "no")).isInstanceOf(NotAllowed.class);
    }

    @Test
    void aWrittenCitationCanSimplyBePaid() {
        Citation one = aCitation().build();
        one.pay(LocalDate.of(2025, 4, 22), "cheque 4471");
        assertThat(one.state()).isSameAs(CitationState.PAID);
        assertThat(last(one.history()).note()).isEqualTo("cheque 4471");
    }

    @Test
    void anUpheldCitationCanBePaidAfterTheHearing() {
        Citation one = aCitation().build();
        one.contest(LocalDate.of(2025, 4, 20), "grounds");
        one.uphold(LocalDate.of(2025, 5, 2));
        one.pay(LocalDate.of(2025, 5, 9));
        assertThat(one.state()).isSameAs(CitationState.PAID);
    }

    @Test
    void aDismissedCitationIsNotOwingAndCannotBePaid() {
        Citation one = aCitation().build();
        one.contest(LocalDate.of(2025, 4, 20), "grounds");
        one.dismiss(LocalDate.of(2025, 5, 2), "scale");
        assertThatThrownBy(() -> one.pay(LocalDate.of(2025, 5, 9))).isInstanceOf(NotAllowed.class);
    }

    @Test
    void aCitationWrittenAgainstTheWrongCarrierIsVoided() {
        Citation one = aCitation().build();
        one.makeVoid(LocalDate.of(2025, 4, 14), "written against the wrong usdot");
        assertThat(one.state()).isSameAs(CitationState.VOID);
        assertThat(one.state().closed()).isTrue();
    }

    @Test
    void aContestedCitationCanStillBeVoidedByTheOffice() {
        Citation one = aCitation().build();
        one.contest(LocalDate.of(2025, 4, 20), "grounds");
        one.makeVoid(LocalDate.of(2025, 4, 21), "wrong day on the ticket");
        assertThat(one.state()).isSameAs(CitationState.VOID);
    }

    @Test
    void aPaidCitationIsRefundedRatherThanVoided() {
        Citation one = aCitation().build();
        one.pay(LocalDate.of(2025, 4, 22));
        assertThatThrownBy(() -> one.makeVoid(LocalDate.of(2025, 4, 23), "wrong carrier"))
                .isInstanceOf(NotAllowed.class)
                .hasMessageContaining("refund");
    }

    @Test
    void aVoidCitationIsNotVoidedTwice() {
        Citation one = aCitation().build();
        one.makeVoid(LocalDate.of(2025, 4, 14), "wrong usdot");
        assertThatThrownBy(() -> one.makeVoid(LocalDate.of(2025, 4, 15), "wrong usdot again"))
                .isInstanceOf(NotAllowed.class);
    }

    @Test
    void voidingSaysWhy() {
        Citation one = aCitation().build();
        assertThatThrownBy(() -> one.makeVoid(LocalDate.of(2025, 4, 14), " ")).isInstanceOf(Impossible.class);
    }

    @Test
    void theCitationReadsOutTheWayItIsReadOverTheTelephone() {
        Citation one = aCitation().permit("25-P-004471").build();
        assertThat(one.lines())
                .containsExactly(
                        "E-25-0417  2025-04-08  Bickford Heavy Haul (1204471)",
                        "  overweight at US201-2 at Fairfield, written by Sgt A Thibodeau",
                        "  under permit 25-P-004471",
                        "  overweight  $580.00",
                        "    over by  12,000 lb",
                        "    due  $580.00",
                        "  written");
    }

    @Test
    void aCitationWithNoPermitSaysNothingAboutOne() {
        assertThat(aCitation().build().lines()).noneMatch(one -> one.contains("permit"));
    }

    @Test
    void aFlatOffenceCarriesNoOverage() {
        Citation one = aCitation(Offence.NO_PERMIT, null, 0).build();
        assertThat(one.over()).isEmpty();
        assertThat(one.amount()).isEqualTo(Dollars.of(500));
    }

    @Test
    void aRepeatOffenderPaysTheMultiplierOnTheCitation() {
        Citation one = aCitation(Offence.OVERWEIGHT, new Pounds(12000), 1).build();
        assertThat(one.amount()).isEqualTo(Dollars.of(1160));
    }

    @Test
    void theOneLineFormSaysEverythingAListNeeds() {
        assertThat(aCitation().build()).hasToString("E-25-0417 Bickford Heavy Haul overweight $580.00 written");
    }

    @Test
    void twoCitationsSayingTheSameThingAreTheSameCitation() {
        Citation first = aCitation().build();
        Citation second = aCitation().build();
        first.pay(LocalDate.of(2025, 4, 22));
        second.pay(LocalDate.of(2025, 4, 25));
        assertThat(first).isEqualTo(second);
    }

    private static Citation.Entry last(List<Citation.Entry> history) {
        return history.get(history.size() - 1);
    }
}
