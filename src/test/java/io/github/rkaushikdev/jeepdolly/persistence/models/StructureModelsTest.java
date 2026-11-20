package io.github.rkaushikdev.jeepdolly.persistence.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.domain.Network;
import io.github.rkaushikdev.jeepdolly.domain.Node;
import io.github.rkaushikdev.jeepdolly.domain.Segment;
import io.github.rkaushikdev.jeepdolly.persistence.IntegrityViolation;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.structures.ReviewState;
import io.github.rkaushikdev.jeepdolly.structures.Structure;
import io.github.rkaushikdev.jeepdolly.structures.StructureBook;
import io.github.rkaushikdev.jeepdolly.structures.StructureKind;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import io.github.rkaushikdev.jeepdolly.support.RoadLog;
import io.github.rkaushikdev.jeepdolly.support.Structures;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The structure table, which is edited, and the review tables, which are not. */
class StructureModelsTest {

    private static final LocalDate MONDAY = LocalDate.of(2025, 7, 7);

    private UnitOfWork session;

    @BeforeEach
    void openOnTheRoadLog() {
        session = Databases.empty().unitOfWork();
        Network network = RoadLog.roadLog();
        for (Node node : network.nodes()) {
            session.add(NodeRow.of(node));
        }
        for (Segment segment : network.segments()) {
            session.add(SegmentRow.of(segment));
        }
        session.commit();
    }

    @AfterEach
    void close() {
        session.close();
    }

    private StructureBook loaded() {
        StructureBook book = Structures.structureBook();
        List<String> idents =
                RoadLog.roadLog().segments().stream().map(Segment::ident).toList();
        for (Structure structure : book.onSegments(idents)) {
            session.add(StructureRow.of(structure));
        }
        session.commit();
        return book;
    }

    private StructureRow structure(String ident) {
        return session.entityManager().find(StructureRow.class, ident);
    }

    private <T> List<T> all(Class<T> type) {
        return session.entityManager()
                .createQuery("select r from " + type.getSimpleName() + " r", type)
                .getResultList();
    }

    @Test
    void theSpansGoOutAsAListAndComeBackAsNumbers() {
        assertThat(StructureRow.spansToText(List.of(120, 120, 120))).isEqualTo("120,120,120");
        assertThat(StructureRow.spansFromText("120,120,120")).containsExactly(120, 120, 120);
        assertThat(StructureRow.spansFromText("")).isEmpty();
    }

    @Test
    void aSpanListThatCannotBeReadIsRefusedRatherThanGuessed() {
        assertThatThrownBy(() -> StructureRow.spansFromText("long,short")).isInstanceOf(Impossible.class);
    }

    @Test
    void everyStructureGoesInAndComesBackTheSame() {
        StructureBook book = loaded();
        session.entityManager().clear();
        List<StructureRow> rows = session.entityManager()
                .createQuery("select s from StructureRow s order by s.ident", StructureRow.class)
                .getResultList();
        assertThat(rows).hasSize(book.size());
        assertThat(rows.get(0).toDomain()).isEqualTo(book.get("1247"));
    }

    @Test
    void aStructureKeepsItsRatingsAndItsPosting() {
        loaded();
        StructureRow row = structure("1533");
        assertThat(row.getOperatingRating()).contains(new Pounds(60000));
        assertThat(row.getPosted()).contains(new Pounds(48000));
        assertThat(row.toDomain().isPosted()).isTrue();
    }

    @Test
    void aNewInspectionEditsTheRowRatherThanAddingOne() {
        loaded();
        StructureRow row = structure("1247");
        row.setOperatingRating(new Pounds(120000));
        row.setInspected(LocalDate.of(2025, 6, 2));
        session.commit();
        assertThat(all(StructureRow.class)).hasSize(6);
        assertThat(structure("1247").toDomain().operatingRating()).contains(new Pounds(120000));
    }

    @Test
    void aDailyRatingAboveThePermitRatingIsRefusedByTheDatabase() {
        loaded();
        structure("1247").setInventoryRating(new Pounds(200000));
        assertThatThrownBy(session::commit).isInstanceOf(IntegrityViolation.class);
    }

    @Test
    void aStructureOnASegmentThatIsNotThereIsRefused() {
        StructureRow nowhere = new StructureRow("9999", "Nowhere", "NOPE-1", StructureKind.BRIDGE);
        nowhere.setSpans("40");
        session.add(nowhere);
        assertThatThrownBy(session::commit).isInstanceOf(IntegrityViolation.class);
    }

    @Test
    void aReviewCarriesItsMovesInTheOrderTheyHappened() {
        ReviewRow review = new ReviewRow("25-04471", MONDAY, ReviewState.WAITING);
        review.setStructures("1247,1533");
        review.addMove(new ReviewMoveRow(0, MONDAY, "counter", ReviewState.WAITING, "opened"));
        review.addMove(new ReviewMoveRow(1, MONDAY, "R Pelletier", ReviewState.WITH_ENGINEER, "picked up"));
        session.add(review);
        session.commit();
        session.entityManager().clear();
        ReviewRow found = session.entityManager().find(ReviewRow.class, "25-04471");
        assertThat(found.getMoves()).extracting(ReviewMoveRow::getPosition).containsExactly(0, 1);
        assertThat(found.getMoves().get(1).getWho()).isEqualTo("R Pelletier");
        assertThat(found).hasToString("<ReviewRow 25-04471 waiting>");
    }

    @Test
    void aMoveWithoutAReviewIsRefused() {
        session.add(new ReviewMoveRow("25-99999", 0, MONDAY, "counter", ReviewState.WAITING));
        assertThatThrownBy(session::commit).isInstanceOf(IntegrityViolation.class);
    }

    @Test
    void deletingAReviewTakesItsMovesWithIt() {
        ReviewRow review = new ReviewRow("25-04471", MONDAY, ReviewState.WAITING);
        review.addMove(new ReviewMoveRow(0, MONDAY, "counter", ReviewState.WAITING, ""));
        session.add(review);
        session.commit();
        session.delete(review);
        session.commit();
        assertThat(all(ReviewMoveRow.class)).isEmpty();
    }

    @Test
    void theReviewStateIsStoredAsItsOwnWords() {
        session.add(new ReviewRow("25-04471", MONDAY, ReviewState.WITH_ENGINEER));
        session.commit();
        Object read = session.entityManager()
                .createNativeQuery("select state from reviews")
                .getSingleResult();
        assertThat(read).isEqualTo("with engineer");
    }
}
