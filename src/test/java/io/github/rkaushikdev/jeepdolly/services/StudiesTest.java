package io.github.rkaushikdev.jeepdolly.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Conflict;
import io.github.rkaushikdev.jeepdolly.NotAllowed;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.persistence.models.ReviewMoveRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.ReviewRow;
import io.github.rkaushikdev.jeepdolly.structures.ReviewState;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The study workflow, kept on rows across days. */
class StudiesTest {

    private static final LocalDate MONDAY = LocalDate.of(2025, 7, 7);
    private static final LocalDate WEDNESDAY = LocalDate.of(2025, 7, 9);
    private static final LocalDate FRIDAY = LocalDate.of(2025, 7, 11);
    private static final LocalDate LATER = LocalDate.of(2025, 8, 4);
    private static final String FIRST = "25-F-000001";

    private UnitOfWork session;
    private EntityManager db;

    @BeforeEach
    void open() {
        session = Databases.empty().unitOfWork();
        db = session.entityManager();
    }

    @AfterEach
    void close() {
        session.close();
    }

    private ReviewRow opened() {
        return opened(FIRST);
    }

    private ReviewRow opened(String reference) {
        ReviewRow row = Studies.openStudy(db, reference, List.of("1247", "1533"), MONDAY);
        session.commit();
        return row;
    }

    @Test
    void aNewStudyWaitsWithOneMoveBehindIt() {
        ReviewRow row = opened();
        assertThat(row.getState()).isSameAs(ReviewState.WAITING);
        assertThat(row.getMoves()).extracting(ReviewMoveRow::getNote).containsExactly("opened");
        assertThat(row.getStructures()).isEqualTo("1247,1533");
    }

    @Test
    void theSameApplicationIsNotQueuedTwice() {
        opened();
        assertThatThrownBy(() -> Studies.openStudy(db, FIRST, List.of("1247"), MONDAY))
                .isInstanceOf(Conflict.class);
    }

    @Test
    void aStudyNobodyOpenedSaysSo() {
        assertThatThrownBy(() -> Studies.rowFor(db, "25-F-999999")).isInstanceOf(NotOnFile.class);
    }

    @Test
    void anEngineerPicksItUpAndTheRowSaysWho() {
        opened();
        ReviewRow row = Studies.assign(db, FIRST, "R Pelletier", WEDNESDAY);
        session.commit();
        assertThat(row.getState()).isSameAs(ReviewState.WITH_ENGINEER);
        assertThat(row.getEngineer()).contains("R Pelletier");
        assertThat(row.getMoves()).extracting(ReviewMoveRow::getPosition).containsExactly(0, 1);
    }

    @Test
    void clearingWritesTheConditionsTheEngineerSigned() {
        opened();
        Studies.assign(db, FIRST, "R Pelletier", WEDNESDAY);
        ReviewRow row = Studies.clear(db, FIRST, FRIDAY, List.of("cross alone", "walking pace"));
        session.commit();
        assertThat(row.getState()).isSameAs(ReviewState.CLEARED);
        assertThat(row.getConditions()).isEqualTo("cross alone|walking pace");
        assertThat(Studies.toDomain(row).conditions()).containsExactly("cross alone", "walking pace");
    }

    @Test
    void refusingWritesTheReasonAndNoConditions() {
        opened();
        Studies.assign(db, FIRST, "R Pelletier", WEDNESDAY);
        ReviewRow row = Studies.refuse(db, FIRST, FRIDAY, "the truss will not take the tridem");
        session.commit();
        assertThat(row.getState()).isSameAs(ReviewState.REFUSED);
        assertThat(row.getMoves().get(row.getMoves().size() - 1).getNote())
                .isEqualTo("the truss will not take the tridem");
    }

    @Test
    void aHoldGoesBackToWaitingWhenTheAnswerArrives() {
        opened();
        Studies.assign(db, FIRST, "R Pelletier", WEDNESDAY);
        Studies.hold(db, FIRST, WEDNESDAY, "send the axle spacings");
        ReviewRow row = Studies.resume(db, FIRST, FRIDAY, "spacings received");
        session.commit();
        assertThat(row.getState()).isSameAs(ReviewState.WAITING);
        assertThat(row.getEngineer()).isEmpty();
        List<ReviewMoveRow> moves = row.getMoves();
        assertThat(moves.subList(moves.size() - 2, moves.size()))
                .extracting(ReviewMoveRow::getState)
                .containsExactly(ReviewState.HELD, ReviewState.WAITING);
    }

    @Test
    void theStateMachineCannotBeGotRoundByTheRows() {
        opened();
        assertThatThrownBy(() -> Studies.clear(db, FIRST, WEDNESDAY)).isInstanceOf(NotAllowed.class);
        assertThatThrownBy(() -> Studies.hold(db, FIRST, WEDNESDAY, "nothing")).isInstanceOf(NotAllowed.class);
    }

    @Test
    void aDecidedStudyWillNotMoveAgain() {
        opened();
        Studies.assign(db, FIRST, "R Pelletier", WEDNESDAY);
        Studies.clear(db, FIRST, FRIDAY);
        session.commit();
        assertThatThrownBy(() -> Studies.refuse(db, FIRST, LATER, "changed my mind"))
                .isInstanceOf(NotAllowed.class);
    }

    @Test
    void theQueueIsOldestFirstAndLosesWhatIsPickedUp() {
        opened("25-F-000002");
        opened(FIRST);
        assertThat(Studies.waiting(db)).extracting(ReviewRow::getReference).containsExactly(FIRST, "25-F-000002");
        Studies.assign(db, FIRST, "R Pelletier", WEDNESDAY);
        session.commit();
        assertThat(Studies.waiting(db)).extracting(ReviewRow::getReference).containsExactly("25-F-000002");
        assertThat(Studies.withEngineer(db, "R Pelletier")).hasSize(1);
    }

    @Test
    void theOverdueListCountsFromTheDayItWasOpened() {
        opened();
        session.commit();
        assertThat(Studies.overdue(db, LATER, 14))
                .extracting(ReviewRow::getReference)
                .containsExactly(FIRST);
        assertThat(Studies.overdue(db, FRIDAY, 14)).isEmpty();
    }

    @Test
    void aDecidedStudyFallsOffTheOverdueList() {
        opened();
        Studies.assign(db, FIRST, "R Pelletier", WEDNESDAY);
        Studies.refuse(db, FIRST, FRIDAY, "no");
        session.commit();
        assertThat(Studies.overdue(db, LATER, 14)).isEmpty();
    }
}
