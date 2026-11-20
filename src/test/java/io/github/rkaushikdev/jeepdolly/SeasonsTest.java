package io.github.rkaushikdev.jeepdolly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Thaw orders and frozen road allowances, filed the way the district signs them. */
class SeasonsTest {

    private static final Pounds STATUTORY = new Pounds(80000);

    private static SeasonOrder thaw() {
        return thaw("25-04", "north", LocalDate.of(2025, 3, 17), LocalDate.of(2025, 5, 2), 65);
    }

    private static SeasonOrder thaw(int share) {
        return thaw("25-04", "north", LocalDate.of(2025, 3, 17), LocalDate.of(2025, 5, 2), share);
    }

    private static SeasonOrder thaw(String number, String region, LocalDate start, LocalDate end, int share) {
        return new SeasonOrder(number, region, SeasonKind.THAW, start, end, share);
    }

    private static SeasonOrder frozen() {
        return new SeasonOrder(
                "25-01", "north", SeasonKind.FROZEN, LocalDate.of(2025, 1, 2), LocalDate.of(2025, 2, 28), 110);
    }

    @Test
    void anOrderCoversBothOfItsEndDays() {
        SeasonOrder order = thaw();
        assertThat(order.covers(LocalDate.of(2025, 3, 17))).isTrue();
        assertThat(order.covers(LocalDate.of(2025, 4, 10))).isTrue();
        assertThat(order.covers(LocalDate.of(2025, 5, 2))).isTrue();
        assertThat(order.covers(LocalDate.of(2025, 3, 16))).isFalse();
        assertThat(order.covers(LocalDate.of(2025, 5, 3))).isFalse();
    }

    @Test
    void anOrderPrintsAsTheClerkReadsItOffTheBoard() {
        assertThat(thaw()).hasToString("25-04 north thaw 65%");
        assertThat(frozen()).hasToString("25-01 north frozen 110%");
    }

    @Test
    void aThawOrderCutsTheLimitAndTruncatesToWholePounds() {
        assertThat(thaw().appliedTo(STATUTORY)).isEqualTo(new Pounds(52000));
        assertThat(thaw(65).appliedTo(new Pounds(34000))).isEqualTo(new Pounds(22100));
        assertThat(thaw(77).appliedTo(new Pounds(20000))).isEqualTo(new Pounds(15400));
    }

    @Test
    void aFrozenOrderRaisesItInstead() {
        assertThat(frozen().appliedTo(STATUTORY)).isEqualTo(new Pounds(88000));
    }

    @Test
    void anOrderThatEndsBeforeItStartsIsATypingMistake() {
        assertThatThrownBy(() -> thaw("25-04", "north", LocalDate.of(2025, 5, 2), LocalDate.of(2025, 3, 17), 65))
                .isInstanceOf(Impossible.class);
    }

    @Test
    void aThawThatReducesNothingIsRefusedAndSoIsTheReverse() {
        assertThatThrownBy(() -> thaw(100)).isInstanceOf(Impossible.class);
        assertThatThrownBy(() -> thaw(115)).isInstanceOf(Impossible.class);
        assertThatThrownBy(() -> new SeasonOrder(
                        "25-09", "north", SeasonKind.FROZEN, LocalDate.of(2025, 1, 2), LocalDate.of(2025, 2, 1), 90))
                .isInstanceOf(Impossible.class);
    }

    @Test
    void aShareNobodyCouldHaveMeantIsRefused() {
        assertThatThrownBy(() -> thaw(0)).isInstanceOf(Impossible.class);
        assertThatThrownBy(() -> new SeasonOrder(
                        "25-09", "north", SeasonKind.FROZEN, LocalDate.of(2025, 1, 2), LocalDate.of(2025, 2, 1), 400))
                .isInstanceOf(Impossible.class);
    }

    @Test
    void theBookHandsBackTheOrderInForceOnTheDay() {
        SeasonBook book = new SeasonBook();
        book.file(frozen());
        book.file(thaw());
        assertThat(book.inForce("north", LocalDate.of(2025, 4, 1)).orElseThrow().number())
                .isEqualTo("25-04");
        assertThat(book.inForce("north", LocalDate.of(2025, 1, 20))
                        .orElseThrow()
                        .number())
                .isEqualTo("25-01");
        assertThat(book.inForce("north", LocalDate.of(2025, 6, 1))).isEmpty();
        assertThat(book.inForce("south", LocalDate.of(2025, 4, 1))).isEmpty();
    }

    @Test
    void aLimitAskedForOutsideAnyOrderIsTheStatutoryOne() {
        SeasonBook book = new SeasonBook();
        book.file(thaw());
        assertThat(book.limitOn("north", LocalDate.of(2025, 4, 1), STATUTORY)).isEqualTo(new Pounds(52000));
        assertThat(book.limitOn("north", LocalDate.of(2025, 6, 1), STATUTORY)).isEqualTo(STATUTORY);
        assertThat(book.limitOn("south", LocalDate.of(2025, 4, 1), STATUTORY)).isEqualTo(STATUTORY);
    }

    @Test
    void aRoadIsPostedOnlyWhileAThawOrderIsRunning() {
        SeasonBook book = new SeasonBook();
        book.file(frozen());
        book.file(thaw());
        assertThat(book.posted("north", LocalDate.of(2025, 4, 1))).isTrue();
        assertThat(book.posted("north", LocalDate.of(2025, 1, 20))).isFalse();
        assertThat(book.posted("north", LocalDate.of(2025, 7, 1))).isFalse();
    }

    @Test
    void twoOrdersOfOneRegionMayNotOverlap() {
        SeasonBook book = new SeasonBook();
        book.file(thaw());
        assertThatThrownBy(() ->
                        book.file(thaw("25-05", "north", LocalDate.of(2025, 4, 20), LocalDate.of(2025, 5, 20), 65)))
                .isInstanceOf(Conflict.class)
                .hasToString("conflict: order 25-05 overlaps 25-04 in north");
    }

    @Test
    void theSameSpanInADifferentRegionIsADifferentOrder() {
        SeasonBook book = new SeasonBook();
        book.file(thaw());
        book.file(thaw("25-06", "downeast", LocalDate.of(2025, 3, 17), LocalDate.of(2025, 5, 2), 65));
        assertThat(book.regions()).containsExactly("downeast", "north");
        assertThat(book.size()).isEqualTo(2);
    }

    @Test
    void anOrderNumberIsUsedOnce() {
        SeasonBook book = new SeasonBook();
        book.file(thaw());
        assertThatThrownBy(() ->
                        book.file(thaw("25-04", "downeast", LocalDate.of(2025, 3, 17), LocalDate.of(2025, 5, 2), 65)))
                .isInstanceOf(Conflict.class)
                .hasMessageContaining("already on file");
    }

    @Test
    void theOrdersOfARegionComeBackInTheOrderTheyRun() {
        SeasonBook book = new SeasonBook();
        book.file(thaw());
        book.file(frozen());
        book.file(thaw("25-08", "north", LocalDate.of(2025, 11, 3), LocalDate.of(2025, 12, 20), 80));
        assertThat(book.ordersFor("north")).extracting(SeasonOrder::number).containsExactly("25-01", "25-04", "25-08");
        assertThat(book.ordersFor("downeast")).isEmpty();
        assertThat(book).hasToString("SeasonBook(3 orders)");
    }
}
