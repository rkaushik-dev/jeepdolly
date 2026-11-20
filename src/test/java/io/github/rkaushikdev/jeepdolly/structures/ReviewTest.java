package io.github.rkaushikdev.jeepdolly.structures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.jeepdolly.Conflict;
import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.NotAllowed;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The review queue, its five states, and the history nobody may edit. */
class ReviewTest {

    private static final LocalDate MONDAY = LocalDate.of(2025, 7, 7);
    private static final LocalDate TUESDAY = LocalDate.of(2025, 7, 8);
    private static final LocalDate FRIDAY = LocalDate.of(2025, 7, 11);
    private static final LocalDate LATER = LocalDate.of(2025, 7, 28);

    private static ReviewQueue queueWith() {
        ReviewQueue queue = new ReviewQueue();
        queue.open("25-04471", List.of("1247"), MONDAY);
        return queue;
    }

    private static Review item() {
        return queueWith().get("25-04471");
    }

    @Test
    void aNewItemWaitsAndCarriesTheLineThatOpenedIt() {
        Review item = item();
        assertThat(item.state()).isSameAs(ReviewState.WAITING);
        assertThat(item.state().open()).isTrue();
        assertThat(item.state().decided()).isFalse();
        assertThat(item.history()).hasSize(1);
        assertThat(item.history().get(0)).hasToString("2025-07-07 counter waiting: opened");
        assertThat(item).hasToString("25-04471 waiting");
    }

    @Test
    void anEngineerPicksItUpAndTheCounterCanSayWhoHasIt() {
        Review item = item();
        item.assign("R Pelletier", TUESDAY);
        assertThat(item.state()).isSameAs(ReviewState.WITH_ENGINEER);
        assertThat(item.engineer()).contains("R Pelletier");
        assertThat(item).hasToString("25-04471 with engineer with R Pelletier");
    }

    @Test
    void aClearedItemCarriesTheConditionsTheEngineerWrote() {
        Review item = item();
        item.assign("R Pelletier", TUESDAY);
        item.clear(FRIDAY, List.of("cross the Kennebec bridge alone", "walking pace over the joint"));
        assertThat(item.state()).isSameAs(ReviewState.CLEARED);
        assertThat(item.state().decided()).isTrue();
        assertThat(item.state().open()).isFalse();
        assertThat(item.conditions().get(0)).isEqualTo("cross the Kennebec bridge alone");
        assertThat(item.decidedOn()).contains(FRIDAY);
    }

    @Test
    void aRefusedItemCarriesTheReasonAndNothingElse() {
        Review item = item();
        item.assign("R Pelletier", TUESDAY);
        item.refuse(FRIDAY, "the 1937 truss will not take the tridem");
        assertThat(item.state()).isSameAs(ReviewState.REFUSED);
        assertThat(item.conditions()).isEmpty();
        assertThat(item.history().get(item.history().size() - 1).note())
                .isEqualTo("the 1937 truss will not take the tridem");
    }

    @Test
    void aHeldItemGoesBackToWaitingWhenTheAnswerComes() {
        Review item = item();
        item.assign("R Pelletier", TUESDAY);
        item.hold(TUESDAY, "send the axle spacings, not the estimates");
        assertThat(item.state()).isSameAs(ReviewState.HELD);
        assertThat(item.state().open()).isTrue();
        item.resume(FRIDAY, "spacings received");
        assertThat(item.state()).isSameAs(ReviewState.WAITING);
        assertThat(item.engineer()).isEmpty();
        List<Move> history = item.history();
        assertThat(history.subList(history.size() - 2, history.size()))
                .extracting(Move::state)
                .containsExactly(ReviewState.HELD, ReviewState.WAITING);
    }

    @Test
    void theMovesThatDoNotMakeSenseAreRefused() {
        Review item = item();
        assertThatThrownBy(() -> item.clear(TUESDAY)).isInstanceOf(NotAllowed.class);
        assertThatThrownBy(() -> item.hold(TUESDAY, "nothing")).isInstanceOf(NotAllowed.class);
        assertThatThrownBy(() -> item.resume(TUESDAY)).isInstanceOf(NotAllowed.class);
        item.assign("R Pelletier", TUESDAY);
        assertThatThrownBy(() -> item.assign("someone else", TUESDAY)).isInstanceOf(NotAllowed.class);
    }

    @Test
    void aDecidedItemCannotBeMovedAgain() {
        Review item = item();
        item.assign("R Pelletier", TUESDAY);
        item.clear(FRIDAY);
        assertThatThrownBy(() -> item.refuse(LATER, "changed my mind")).isInstanceOf(NotAllowed.class);
        assertThatThrownBy(() -> item.hold(LATER, "changed my mind")).isInstanceOf(NotAllowed.class);
    }

    @Test
    void aMoveDatedBeforeTheItemExistedIsRefused() {
        Review item = item();
        assertThatThrownBy(() -> item.assign("R Pelletier", LocalDate.of(2025, 1, 1)))
                .isInstanceOf(Impossible.class);
    }

    @Test
    void anItemNeedsAnApplicationToBeOpenedAgainst() {
        assertThatThrownBy(() -> new ReviewQueue().open("   ", List.of("1247"), MONDAY))
                .isInstanceOf(Impossible.class);
    }

    @Test
    void theSameApplicationIsNotQueuedTwice() {
        ReviewQueue queue = queueWith();
        Conflict refusal = catchThrowableOfType(Conflict.class, () -> queue.open("25-04471", List.of("1533"), TUESDAY));
        assertThat(refusal.facts().get("reference")).isEqualTo("25-04471");
    }

    @Test
    void askingForAReviewThatWasNeverOpenedSaysSo() {
        assertThatThrownBy(() -> new ReviewQueue().get("25-99999")).isInstanceOf(NotOnFile.class);
    }

    @Test
    void theWaitingListIsOldestFirstBecauseThatIsTheOrderWorked() {
        ReviewQueue queue = new ReviewQueue();
        queue.open("25-00003", List.of("1247"), FRIDAY);
        queue.open("25-00001", List.of("1533"), MONDAY);
        queue.open("25-00002", List.of("3310"), TUESDAY);
        assertThat(queue.waiting()).extracting(Review::reference).containsExactly("25-00001", "25-00002", "25-00003");
    }

    @Test
    void anEngineerCanAskForWhatIsOnTheirOwnDesk() {
        ReviewQueue queue = new ReviewQueue();
        Review first = queue.open("25-00001", List.of("1247"), MONDAY);
        Review second = queue.open("25-00002", List.of("1533"), MONDAY);
        first.assign("R Pelletier", TUESDAY);
        second.assign("J Ouellette", TUESDAY);
        assertThat(queue.withEngineer("R Pelletier"))
                .extracting(Review::reference)
                .containsExactly("25-00001");
        assertThat(queue.openItems()).hasSize(2);
    }

    @Test
    void theOverdueListIsWhatTheSupervisorReadsOnMonday() {
        ReviewQueue queue = new ReviewQueue();
        queue.open("25-00001", List.of("1247"), MONDAY);
        Review old = queue.open("25-00002", List.of("1533"), LocalDate.of(2025, 6, 2));
        assertThat(old.waitingDays(LATER)).isEqualTo(56);
        assertThat(queue.overdue(LATER, 21)).extracting(Review::reference).containsExactly("25-00002");
    }

    @Test
    void aDecidedItemFallsOffTheOverdueList() {
        ReviewQueue queue = new ReviewQueue();
        Review item = queue.open("25-00002", List.of("1533"), LocalDate.of(2025, 6, 2));
        item.assign("R Pelletier", LocalDate.of(2025, 6, 3));
        item.refuse(LocalDate.of(2025, 6, 4), "no");
        assertThat(queue.overdue(LATER, 21)).isEmpty();
    }

    @Test
    void theItemsTouchingOneStructureCanBeListed() {
        ReviewQueue queue = new ReviewQueue();
        queue.open("25-00001", List.of("1247", "1533"), MONDAY);
        queue.open("25-00002", List.of("3310"), MONDAY);
        assertThat(queue.touching("1533")).extracting(Review::reference).containsExactly("25-00001");
        assertThat(queue.size()).isEqualTo(2);
    }
}
