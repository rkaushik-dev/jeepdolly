package io.github.rkaushikdev.jeepdolly.permits;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.jeepdolly.Conflict;
import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Unreadable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Permit numbers, and the gapless counter behind them. */
class NumberingTest {

    private static final LocalDate APRIL = LocalDate.of(2025, 4, 8);

    @Test
    void aNumberPrintsShortEnoughToReadOverTheTelephone() {
        PermitNumber number = new PermitNumber(2025, Series.SINGLE, 4471);
        assertThat(number).hasToString("25-P-004471");
        assertThat(number.repr()).isEqualTo("PermitNumber(25-P-004471)");
    }

    @Test
    void aNumberReadsBackFromWhatWasWrittenOnTheFax() {
        PermitNumber number = PermitNumber.parse("25-S-000012");
        assertThat(number.year()).isEqualTo(2025);
        assertThat(number.series()).isSameAs(Series.SUPERLOAD);
        assertThat(number.sequence()).isEqualTo(12);
    }

    @Test
    void somethingThatIsNotANumberIsRefusedAndQuotedBack() {
        Unreadable refusal = catchThrowableOfType(Unreadable.class, () -> PermitNumber.parse("permit 4471"));
        assertThat(refusal.facts().get("written")).isEqualTo("permit 4471");
        assertThatThrownBy(() -> PermitNumber.parse("25-P-4471")).isInstanceOf(Unreadable.class);
        assertThatThrownBy(() -> PermitNumber.parse("2025-P-004471")).isInstanceOf(Unreadable.class);
    }

    @Test
    void aNumberOutsideWhatTheOfficeIssuesIsRefused() {
        assertThatThrownBy(() -> new PermitNumber(1999, Series.SINGLE, 1)).isInstanceOf(Impossible.class);
        assertThatThrownBy(() -> new PermitNumber(2025, Series.SINGLE, 0)).isInstanceOf(Impossible.class);
        assertThatThrownBy(() -> new PermitNumber(2025, Series.SINGLE, 1000000)).isInstanceOf(Impossible.class);
    }

    @Test
    void numbersSortByYearThenSeriesThenSequence() {
        List<PermitNumber> numbers = List.of(
                new PermitNumber(2025, Series.SINGLE, 12),
                new PermitNumber(2024, Series.SINGLE, 900),
                new PermitNumber(2025, Series.ANNUAL, 3));
        assertThat(numbers.stream().sorted().map(PermitNumber::toString))
                .containsExactly("24-P-000900", "25-A-000003", "25-P-000012");
    }

    @Test
    void theCounterStartsAtOneAndNeverSkips() {
        Counter counter = new Counter();
        List<Integer> taken = IntStream.range(0, 5)
                .mapToObj(one -> counter.take(2025, Series.SINGLE))
                .toList();
        assertThat(taken).containsExactly(1, 2, 3, 4, 5);
        assertThat(counter.peek(2025, Series.SINGLE)).isEqualTo(6);
        assertThat(counter.issued(2025, Series.SINGLE)).isEqualTo(5);
    }

    @Test
    void eachSeriesAndEachYearCountsOnItsOwn() {
        Counter counter = new Counter();
        counter.take(2025, Series.SINGLE);
        counter.take(2025, Series.SINGLE);
        assertThat(counter.take(2025, Series.ANNUAL)).isEqualTo(1);
        assertThat(counter.take(2026, Series.SINGLE)).isEqualTo(1);
        assertThat(counter.issued(2025, Series.SINGLE)).isEqualTo(2);
    }

    @Test
    void theCounterCanBeStartedFromWhereThePaperLedgerStopped() {
        Counter counter = new Counter(Map.of(new Counter.Key(2025, Series.SINGLE), 4471));
        assertThat(counter.take(2025, Series.SINGLE)).isEqualTo(4471);
        assertThat(counter.take(2025, Series.SINGLE)).isEqualTo(4472);
    }

    @Test
    void aFullSeriesRefusesRatherThanWrappingRound() {
        Counter counter = new Counter(Map.of(new Counter.Key(2025, Series.SINGLE), 1000000));
        assertThatThrownBy(() -> counter.take(2025, Series.SINGLE)).isInstanceOf(Conflict.class);
    }

    @Test
    void twoThreadsTakingAtOnceDoNotGetTheSameNumber() throws InterruptedException {
        Counter counter = new Counter();
        List<Integer> taken = Collections.synchronizedList(new ArrayList<>());
        Runnable grab = () -> {
            for (int one = 0; one < 50; one++) {
                taken.add(counter.take(2025, Series.SINGLE));
            }
        };
        List<Thread> threads =
                IntStream.range(0, 4).mapToObj(one -> new Thread(grab)).toList();
        for (Thread thread : threads) {
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        assertThat(taken).hasSize(200);
        assertThat(taken.stream().sorted().toList())
                .isEqualTo(IntStream.rangeClosed(1, 200).boxed().toList());
    }

    @Test
    void theNumberingHandsOutAWholeNumberForADay() {
        Numbering numbering = new Numbering();
        PermitNumber first = numbering.nextNumber(APRIL);
        PermitNumber second = numbering.nextNumber(APRIL, Series.SUPERLOAD);
        assertThat(first).hasToString("25-P-000001");
        assertThat(second).hasToString("25-S-000001");
        assertThat(numbering.issuedIn(2025)).isEqualTo(1);
    }

    @Test
    void aReplacementTakesANumberOutOfItsOwnSeries() {
        Numbering numbering = new Numbering();
        PermitNumber original = numbering.nextNumber(APRIL);
        PermitNumber replacement = numbering.replacementFor(original, APRIL);
        assertThat(replacement.series()).isSameAs(Series.REPLACEMENT);
        assertThat(replacement).hasToString("25-R-000001");
    }

    @Test
    void aReplacementOfAReplacementIsRefused() {
        Numbering numbering = new Numbering();
        PermitNumber original = numbering.nextNumber(APRIL);
        PermitNumber replacement = numbering.replacementFor(original, APRIL);
        assertThatThrownBy(() -> numbering.replacementFor(replacement, APRIL)).isInstanceOf(Impossible.class);
    }
}
