package io.github.rkaushikdev.jeepdolly.limits;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.domain.HighwayClass;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The statutory tables, and which one is in force when. */
class StatutoryTest {

    private static final LocalDate MARCH = LocalDate.of(2025, 3, 3);

    private static StatutoryTable arterial(LocalDate effective, int single, int tandem, int gross) {
        return new StatutoryTable(
                HighwayClass.ARTERIAL,
                effective,
                Map.of(1, new Pounds(single), 2, new Pounds(tandem)),
                new Pounds(7000),
                new Pounds(gross),
                600);
    }

    @Test
    void theInterstateTableIsTheFederalOne() {
        StatutoryTable table = StatutoryBook.standard().on(HighwayClass.INTERSTATE, MARCH);
        assertThat(table.groupLimit(1)).isEqualTo(new Pounds(20000));
        assertThat(table.groupLimit(2)).isEqualTo(new Pounds(34000));
        assertThat(table.gross()).isEqualTo(new Pounds(80000));
        assertThat(table.tireRate()).isEqualTo(500);
    }

    @Test
    void theStateRoadsCarryMoreThanTheInterstateDoes() {
        StatutoryBook book = StatutoryBook.standard();
        StatutoryTable arterial = book.on(HighwayClass.ARTERIAL, MARCH);
        StatutoryTable interstate = book.on(HighwayClass.INTERSTATE, MARCH);
        assertThat(arterial.groupLimit(2)).isEqualTo(new Pounds(41000));
        assertThat(arterial.gross()).isEqualTo(new Pounds(100000));
        assertThat(arterial.groupLimit(2)).isGreaterThan(interstate.groupLimit(2));
    }

    @Test
    void aGroupBiggerThanTheTableNamesGetsThePublishedAllowance() {
        StatutoryTable table = StatutoryBook.standard().on(HighwayClass.ARTERIAL, MARCH);
        assertThat(table.largestNamed()).isEqualTo(4);
        assertThat(table.groupLimit(5)).isEqualTo(new Pounds(63000));
        assertThat(table.groupLimit(7)).isEqualTo(new Pounds(77000));
    }

    @Test
    void aGroupOfNoAxlesIsNotAQuestionTheTableAnswers() {
        assertThatThrownBy(() -> StatutoryBook.standard()
                        .on(HighwayClass.ARTERIAL, MARCH)
                        .groupLimit(0))
                .isInstanceOf(Impossible.class);
    }

    @Test
    void theOverageOnAGroupIsTheAmountAndNeverAVerdict() {
        StatutoryTable table = StatutoryBook.standard().on(HighwayClass.ARTERIAL, MARCH);
        assertThat(table.overGroup(2, new Pounds(45000))).isEqualTo(new Pounds(4000));
        assertThat(table.overGroup(2, new Pounds(41000))).isEqualTo(new Pounds(0));
        assertThat(table.overGross(new Pounds(132400))).isEqualTo(new Pounds(32400));
        assertThat(table.overGross(new Pounds(80000))).isEqualTo(new Pounds(0));
    }

    @Test
    void aTablePrintsTheClassAndTheDayItTookEffect() {
        StatutoryTable table = StatutoryBook.standard(LocalDate.of(2024, 7, 1)).on(HighwayClass.STATE_AID, MARCH);
        assertThat(table).hasToString("state aid table of 2024-07-01");
    }

    @Test
    void aTableWhoseLimitsDoNotClimbWithTheAxlesIsRefused() {
        assertThatThrownBy(() -> arterial(MARCH, 22400, 22400, 100000))
                .isInstanceOf(Impossible.class)
                .hasMessageContaining("not above");
    }

    @Test
    void aTableThatNamesNoSingleAxleIsNotATable() {
        assertThatThrownBy(() -> new StatutoryTable(
                        HighwayClass.ARTERIAL,
                        MARCH,
                        Map.of(2, new Pounds(41000)),
                        new Pounds(7000),
                        new Pounds(100000),
                        600))
                .isInstanceOf(Impossible.class);
    }

    @Test
    void aTireRateOfNothingIsRefused() {
        assertThatThrownBy(() -> new StatutoryTable(
                        HighwayClass.ARTERIAL,
                        MARCH,
                        Map.of(1, new Pounds(22400), 2, new Pounds(41000)),
                        new Pounds(7000),
                        new Pounds(100000),
                        0))
                .isInstanceOf(Impossible.class);
    }

    @Test
    void theTableInForceIsTheLatestOneThatHadTakenEffect() {
        StatutoryBook book = StatutoryBook.standard(LocalDate.of(2024, 1, 1));
        StatutoryTable later = book.on(HighwayClass.ARTERIAL, MARCH);
        assertThat(later.effective()).isEqualTo(LocalDate.of(2024, 1, 1));
        book.file(arterial(LocalDate.of(2025, 2, 1), 23000, 42000, 104000));
        assertThat(book.on(HighwayClass.ARTERIAL, MARCH).gross()).isEqualTo(new Pounds(104000));
        assertThat(book.on(HighwayClass.ARTERIAL, LocalDate.of(2025, 1, 1)).gross())
                .isEqualTo(new Pounds(100000));
    }

    @Test
    void aPermitArguedAboutLaterIsJudgedByTheTableOfItsOwnDay() {
        StatutoryBook book = StatutoryBook.standard(LocalDate.of(2024, 1, 1));
        book.file(arterial(LocalDate.of(2025, 6, 1), 23000, 42000, 104000));
        StatutoryTable issued = book.on(HighwayClass.ARTERIAL, LocalDate.of(2025, 3, 3));
        assertThat(issued.groupLimit(2)).isEqualTo(new Pounds(41000));
    }

    @Test
    void askingBeforeAnyTableTookEffectSaysSo() {
        StatutoryBook book = StatutoryBook.standard(LocalDate.of(2024, 1, 1));
        NotOnFile refusal =
                catchThrowableOfType(NotOnFile.class, () -> book.on(HighwayClass.ARTERIAL, LocalDate.of(2023, 5, 1)));
        assertThat(refusal.facts().get("highway_class")).isEqualTo("arterial");
    }

    @Test
    void twoTablesForOneClassOnOneDayIsRefused() {
        StatutoryBook book = StatutoryBook.standard(LocalDate.of(2024, 1, 1));
        assertThatThrownBy(() -> book.file(arterial(LocalDate.of(2024, 1, 1), 23000, 42000, 104000)))
                .isInstanceOf(Impossible.class);
    }

    @Test
    void theBookKnowsWhichClassesItCovers() {
        StatutoryBook book = StatutoryBook.standard();
        assertThat(book.size()).isEqualTo(4);
        assertThat(book.classes()).contains(HighwayClass.TOWN_WAY);
    }
}
