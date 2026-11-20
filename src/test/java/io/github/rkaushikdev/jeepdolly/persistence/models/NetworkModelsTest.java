package io.github.rkaushikdev.jeepdolly.persistence.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.Miles;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.domain.Jurisdiction;
import io.github.rkaushikdev.jeepdolly.domain.Network;
import io.github.rkaushikdev.jeepdolly.domain.Node;
import io.github.rkaushikdev.jeepdolly.domain.RestrictionKind;
import io.github.rkaushikdev.jeepdolly.domain.Route;
import io.github.rkaushikdev.jeepdolly.domain.Segment;
import io.github.rkaushikdev.jeepdolly.persistence.IntegrityViolation;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import io.github.rkaushikdev.jeepdolly.support.RoadLog;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The road log tables, their constraints, and the order rows have to go in. */
class NetworkModelsTest {

    private UnitOfWork session;

    @BeforeEach
    void open() {
        session = Databases.empty().unitOfWork();
    }

    @AfterEach
    void close() {
        session.close();
    }

    private Network loaded() {
        Network network = RoadLog.roadLog();
        for (Node node : network.nodes()) {
            session.add(NodeRow.of(node));
        }
        for (Segment segment : network.segments()) {
            session.add(SegmentRow.of(segment));
        }
        session.commit();
        return network;
    }

    private <T> T find(Class<T> type, Object key) {
        return session.entityManager().find(type, key);
    }

    private static Segment anson() {
        return new Segment(
                "US201-4",
                Route.parse("US 201"),
                "anson",
                "solon",
                Miles.of(9, 10),
                new Jurisdiction("Somerset", "Anson", 4));
    }

    @Test
    void theWholeRoadLogGoesInAndComesBackTheSame() {
        Network network = loaded();
        session.entityManager().clear();
        List<SegmentRow> rows = session.entityManager()
                .createQuery("select s from SegmentRow s order by s.ident", SegmentRow.class)
                .getResultList();
        assertThat(rows).hasSize(network.size());
        assertThat(rows.stream().map(SegmentRow::toDomain).toList()).isEqualTo(network.segments());
    }

    @Test
    void aSegmentKeepsItsRouteDesignationInPieces() {
        loaded();
        SegmentRow row = find(SegmentRow.class, "SA148-1");
        assertThat(row.getRouteKind()).isEqualTo("SA");
        assertThat(row.getRouteNumber()).isEqualTo(148);
        assertThat(row.toDomain().route()).isEqualTo(Route.parse("SA 148"));
    }

    @Test
    void aSegmentKeepsItsLengthAsHundredthsAndItsTypeOnTheWayOut() {
        loaded();
        assertThat(find(SegmentRow.class, "US201-2").getLength()).isEqualTo(Miles.of(12, 40));
        Object read = session.entityManager()
                .createNativeQuery("select length_hundredths from segments where ident = 'US201-2'")
                .getSingleResult();
        assertThat(read).isEqualTo(1240);
    }

    @Test
    void aNodeAndItsSegmentAddedTogetherGoInTheRightOrder() {
        session.add(new NodeRow("anson", "Anson"));
        session.add(new NodeRow("solon", "Solon"));
        session.add(SegmentRow.of(anson()));
        session.commit();
        // Hibernate keeps the segment it was handed, which never named a node row, so the row is
        // read back fresh, which loads the relationship from the table.
        session.entityManager().clear();
        assertThat(find(SegmentRow.class, "US201-4").getStart().orElseThrow().getTown())
                .isEqualTo("Anson");
    }

    @Test
    void aSegmentNamingANodeThatIsNotThereIsRefused() {
        session.add(NodeRow.of(new Node("anson", "Anson")));
        session.commit();
        session.add(SegmentRow.of(anson()));
        assertThatThrownBy(session::commit).isInstanceOf(IntegrityViolation.class);
    }

    @Test
    void aSegmentOfNoLengthIsRefusedByTheDatabase() {
        loaded();
        find(SegmentRow.class, "US2-1").setLength(new Miles(0));
        assertThatThrownBy(session::commit).isInstanceOf(IntegrityViolation.class);
    }

    @Test
    void aSegmentThatStartsWhereItEndsIsRefusedByTheDatabase() {
        loaded();
        SegmentRow row = find(SegmentRow.class, "US2-1");
        row.setEndNode(row.getStartNode());
        assertThatThrownBy(session::commit).isInstanceOf(IntegrityViolation.class);
    }

    @Test
    void aDistrictTheStateDoesNotHaveIsRefusedByTheDatabase() {
        loaded();
        find(SegmentRow.class, "US2-1").setDistrict(12);
        assertThatThrownBy(session::commit).isInstanceOf(IntegrityViolation.class);
    }

    @Test
    void aRestrictionHangsOffTheSegmentItRestricts() {
        loaded();
        RestrictionRow clearance = new RestrictionRow("R-1", "US2-1", RestrictionKind.CLEARANCE, "railroad bridge");
        clearance.setHeight(Inches.of(13, 9));
        session.add(clearance);
        session.commit();
        session.entityManager().clear();
        RestrictionRow row = find(RestrictionRow.class, "R-1");
        assertThat(row.getHeight()).contains(new Inches(165));
        assertThat(row.getSegment().orElseThrow().getRouteNumber()).isEqualTo(2);
        assertThat(row).hasToString("<RestrictionRow R-1 clearance>");
    }

    @Test
    void aRestrictionOnASegmentThatIsNotThereIsRefused() {
        session.add(new RestrictionRow("R-2", "NOPE-1", RestrictionKind.CLOSURE, "bridge out"));
        assertThatThrownBy(session::commit).isInstanceOf(IntegrityViolation.class);
    }

    @Test
    void aRestrictionThatLiftsBeforeItStartsIsRefusedByTheDatabase() {
        loaded();
        RestrictionRow backwards = new RestrictionRow("R-3", "US2-1", RestrictionKind.CLOSURE, "culvert");
        backwards.setEffective(LocalDate.of(2025, 8, 1));
        backwards.setExpires(LocalDate.of(2025, 6, 1));
        session.add(backwards);
        assertThatThrownBy(session::commit).isInstanceOf(IntegrityViolation.class);
    }

    @Test
    void aCurfewIsStoredAsDaysAndTwoClockTimes() {
        loaded();
        RestrictionRow curfew = new RestrictionRow("R-4", "US201-1", RestrictionKind.CURFEW, "rush hour");
        curfew.setCurfewDays("01234");
        curfew.setCurfewFrom(LocalTime.of(6, 30));
        curfew.setCurfewTo(LocalTime.of(8, 30));
        session.add(curfew);
        session.commit();
        RestrictionRow row = find(RestrictionRow.class, "R-4");
        assertThat(row.getCurfewDays()).contains("01234");
        assertThat(row.getCurfewFrom()).contains(LocalTime.of(6, 30));
        assertThat(row.getWeight()).isEmpty();
    }

    @Test
    void aPostedStructureRestrictionCarriesAWeight() {
        loaded();
        RestrictionRow posted = new RestrictionRow("R-5", "SA148-1", RestrictionKind.WEIGHT, "Carrabassett bridge");
        posted.setWeight(new Pounds(48000));
        session.add(posted);
        session.commit();
        assertThat(find(RestrictionRow.class, "R-5").getWeight()).contains(new Pounds(48000));
    }

    @Test
    void aLiftedRestrictionIsDeletedRatherThanKept() {
        loaded();
        session.add(new RestrictionRow("R-6", "US2-1", RestrictionKind.CLOSURE, "culvert"));
        session.commit();
        session.delete(find(RestrictionRow.class, "R-6"));
        session.commit();
        assertThat(session.entityManager()
                        .createQuery("select r from RestrictionRow r", RestrictionRow.class)
                        .getResultList())
                .isEmpty();
        assertThat(find(SegmentRow.class, "US2-1")).isNotNull();
    }
}
