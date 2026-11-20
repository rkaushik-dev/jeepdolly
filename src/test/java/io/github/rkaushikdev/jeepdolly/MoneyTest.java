package io.github.rkaushikdev.jeepdolly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.jeepdolly.support.Signatures;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Money in whole cents, and the two divisions the fee schedule asks for. */
class MoneyTest {

    @Test
    void anAmountPrintsWithADollarSignAndTwoPlaces() {
        assertThat(Dollars.of(1250)).hasToString("$1,250.00");
        assertThat(new Dollars(4)).hasToString("$0.04");
        assertThat(new Dollars(-1550)).hasToString("-$15.50");
        assertThat(new Dollars(1550).repr()).isEqualTo("Dollars(1550)");
    }

    @Test
    void anAmountReadsOffAScheduleOrARemittance() {
        assertThat(Dollars.parse("$1,250.00")).isEqualTo(new Dollars(125000));
        assertThat(Dollars.parse("1250")).isEqualTo(new Dollars(125000));
        assertThat(Dollars.parse("$40")).isEqualTo(new Dollars(4000));
        assertThat(Dollars.parse("-$15.50")).isEqualTo(new Dollars(-1550));
        assertThat(Dollars.parse(" 40.25 ")).isEqualTo(new Dollars(4025));
    }

    @Test
    void anAmountThatCannotBeReadIsRefusedAndNamesWhatArrived() {
        Unreadable refusal = catchThrowableOfType(Unreadable.class, () -> Dollars.parse("forty dollars"));
        assertThat(refusal.facts().get("written")).isEqualTo("forty dollars");
        assertThatThrownBy(() -> Dollars.parse("40.5")).isInstanceOf(Unreadable.class);
        assertThatThrownBy(() -> Dollars.parse("")).isInstanceOf(Unreadable.class);
    }

    @Test
    void moneyIsNeverBuiltFromAFloat() {
        // A fraction or a flag cannot be money: the only way in takes whole cents.
        Signatures.constructorsTakeOnly(Dollars.class, long.class);
    }

    @Test
    void amountsAddSubtractAndMultiplyByACount() {
        assertThat(Dollars.of(40).plus(Dollars.of(2, 50))).isEqualTo(new Dollars(4250));
        assertThat(Dollars.of(40).minus(Dollars.of(2, 50))).isEqualTo(new Dollars(3750));
        assertThat(Dollars.of(40).times(3)).isEqualTo(new Dollars(12000));
        assertThat(new Dollars(4025).negated()).isEqualTo(new Dollars(-4025));
        // An amount never adds a bare number: addition takes only another amount.
        Signatures.methodTakesOnly(Dollars.class, "plus", Dollars.class);
    }

    @Test
    void wholeDollarsRoundsTowardZeroForALineWithNoCents() {
        assertThat(new Dollars(4099).wholeDollars()).isEqualTo(40);
        assertThat(new Dollars(-4099).wholeDollars()).isEqualTo(-40);
    }

    @Test
    void aPercentOfAnAmountRoundsHalfUp() {
        assertThat(Dollars.of(100).percent(10)).isEqualTo(new Dollars(1000));
        assertThat(new Dollars(1005).percent(50)).isEqualTo(new Dollars(503));
        assertThat(new Dollars(1001).percent(50)).isEqualTo(new Dollars(501));
        assertThat(Dollars.of(40).percent(0)).isEqualTo(new Dollars(0));
    }

    @Test
    void aNegativeShareIsNotAShare() {
        assertThatThrownBy(() -> Dollars.of(40).percent(-10)).isInstanceOf(Impossible.class);
    }

    @Test
    void aSplitAddsBackToWhatWasSplit() {
        List<Dollars> parts = Dollars.of(10).split(3);
        assertThat(parts).extracting(Dollars::cents).containsExactly(334L, 333L, 333L);
        assertThat(Dollars.total(parts)).isEqualTo(Dollars.of(10));
    }

    @Test
    void aSplitThatDividesEvenlyLeavesNoRemainderAnywhere() {
        List<Dollars> parts = Dollars.of(90).split(3);
        assertThat(parts).extracting(Dollars::toString).containsExactly("$30.00", "$30.00", "$30.00");
    }

    @Test
    void aSplitOneWayIsTheWholeAmount() {
        assertThat(Dollars.of(40).split(1)).containsExactly(Dollars.of(40));
    }

    @Test
    void aSplitNoWaysAtAllIsRefused() {
        assertThatThrownBy(() -> Dollars.of(40).split(0)).isInstanceOf(Impossible.class);
    }

    @Test
    void amountsSortAndATotalOfNothingIsNothing() {
        List<Dollars> fees = new ArrayList<>(List.of(Dollars.of(40), Dollars.of(15), Dollars.of(112, 50)));
        assertThat(Dollars.total(fees)).isEqualTo(Dollars.of(167, 50));
        Collections.sort(fees);
        assertThat(fees.get(0)).isEqualTo(Dollars.of(15));
        assertThat(Dollars.total(List.of())).isEqualTo(new Dollars(0));
    }
}
