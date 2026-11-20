package io.github.rkaushikdev.jeepdolly.structures;

import static io.github.rkaushikdev.jeepdolly.support.Streams.jeepedLowboy;
import static io.github.rkaushikdev.jeepdolly.support.Streams.tractorLowboy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.support.Structures;
import org.junit.jupiter.api.Test;

/** The window of axles a structure feels, which is rarely the whole train. */
class LoadingTest {

    private static final StructureBook BOOK = Structures.structureBook();

    @Test
    void aLongSpanCarriesTheWholeTrain() {
        LoadWindow window = Loading.loadOnSpan(tractorLowboy(), 120);
        assertThat(window.wholeTrain()).isTrue();
        assertThat(window.count()).isEqualTo(6);
        assertThat(window.weight()).isEqualTo(new Pounds(94000));
        assertThat(window.extent()).isEqualTo(new Inches(538));
    }

    @Test
    void aShortSpanCarriesOnlyTheWorstRunThatFits() {
        LoadWindow window = Loading.loadOnSpan(tractorLowboy(), 14);
        assertThat(window.count()).isEqualTo(3);
        assertThat(window.first()).isEqualTo(3);
        assertThat(window.weight()).isEqualTo(new Pounds(48000));
        assertThat(window.extent()).isEqualTo(new Inches(104));
        assertThat(window.wholeTrain()).isFalse();
    }

    @Test
    void theWorstRunIsSearchedForAndNotAssumedToBeTheBack() {
        LoadWindow window = Loading.loadOnSpan(jeepedLowboy(), 14);
        assertThat(window.first()).isEqualTo(5);
        assertThat(window.last()).isEqualTo(7);
        assertThat(window.weight()).isEqualTo(new Pounds(60000));
    }

    @Test
    void aRunThatEndsExactlyOnTheAbutmentStillCounts() {
        LoadWindow window = Loading.loadOnSpan(jeepedLowboy(), 48);
        assertThat(window.extent()).isEqualTo(Inches.of(48));
        assertThat(window.weight()).isEqualTo(new Pounds(153000));
        assertThat(window.first()).isEqualTo(1);
        assertThat(window.last()).isEqualTo(8);
    }

    @Test
    void aRunOneInchLongerThanTheSpanDoesNotCount() {
        LoadWindow window = Loading.loadOnSpan(jeepedLowboy(), 47);
        assertThat(window.extent()).isLessThan(Inches.of(48));
        assertThat(window.weight()).isLessThan(new Pounds(153000));
    }

    @Test
    void aWindowPrintsTheWayAWorksheetLineReads() {
        assertThat(Loading.loadOnSpan(tractorLowboy(), 14)).hasToString("axles 4 to 6, 48,000 lb over 8 ft 8 in");
    }

    @Test
    void aSpanOfNoLengthIsNotASpan() {
        assertThatThrownBy(() -> Loading.loadOnSpan(tractorLowboy(), 0)).isInstanceOf(Impossible.class);
    }

    @Test
    void aStructureIsGovernedByItsLongestSingleSpan() {
        Structure kennebec = BOOK.get("1247");
        assertThat(kennebec.longestSpan()).isEqualTo(120);
        assertThat(Loading.governingWeight(kennebec, tractorLowboy())).isEqualTo(new Pounds(94000));
        Structure culvert = BOOK.get("3310");
        assertThat(culvert.longestSpan()).isEqualTo(14);
        assertThat(Loading.governingWeight(culvert, tractorLowboy())).isEqualTo(new Pounds(48000));
    }

    @Test
    void theReliefIsWhatTheStructureNeverSeesAtOnce() {
        Structure culvert = BOOK.get("3310");
        assertThat(Loading.relief(culvert, tractorLowboy())).isEqualTo(new Pounds(46000));
        assertThat(Loading.relief(BOOK.get("1247"), tractorLowboy())).isEqualTo(new Pounds(0));
    }

    @Test
    void aStructureThatCarriesNoTrafficIsNotAskedAboutLoading() {
        assertThatThrownBy(() -> Loading.loadOn(BOOK.get("2201"), tractorLowboy()))
                .isInstanceOf(Impossible.class)
                .hasMessageContaining("does not carry traffic");
    }

    @Test
    void aSpanTooShortForTwoAxlesCarriesTheHeaviestOne() {
        LoadWindow window = Loading.loadOnSpan(tractorLowboy(), 1);
        assertThat(window.count()).isEqualTo(1);
        assertThat(window.weight()).isEqualTo(new Pounds(17000));
        assertThat(window.extent()).isEqualTo(new Inches(0));
    }
}
