package io.github.rkaushikdev.jeepdolly.services;

import static io.github.rkaushikdev.jeepdolly.support.RoadLog.roadLog;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Miles;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.persistence.models.SegmentRow;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Loading the published road log, badly formed lines and all. */
class RoadlogTest {

    private static final String LINE = "US201-2|US 201|fairfield|skowhegan|1240|Somerset|Fairfield|3|0";

    private UnitOfWork work;

    private EntityManager seeded() {
        work = Databases.seeded().unitOfWork();
        return work.entityManager();
    }

    @AfterEach
    void close() {
        if (work != null) {
            work.close();
        }
    }

    @Test
    void aSegmentIsWrittenTheWayTheStatePublishesIt() {
        assertThat(RoadLogFile.written(roadLog().segment("US201-2"))).isEqualTo(LINE);
    }

    @Test
    void aPublishedLineReadsBackIntoTheSegmentItCameFrom() {
        assertThat(RoadLogFile.parse(LINE)).isEqualTo(roadLog().segment("US201-2"));
    }

    @Test
    void aLineWithTheWrongFieldCountIsRefused() {
        assertThatThrownBy(() -> RoadLogFile.parse("US201-2|US 201|fairfield"))
                .isInstanceOf(Impossible.class)
                .hasMessageContaining("9 fields");
    }

    @Test
    void aLengthThatIsNotANumberIsRefused() {
        assertThatThrownBy(() -> RoadLogFile.parse("US201-2|US 201|fairfield|skowhegan|twelve|Somerset|Fairfield|3|0"))
                .isInstanceOf(Impossible.class);
    }

    @Test
    void aDistrictThatIsNotANumberIsRefused() {
        assertThatThrownBy(
                        () -> RoadLogFile.parse("US201-2|US 201|fairfield|skowhegan|1240|Somerset|Fairfield|north|0"))
                .isInstanceOf(Impossible.class);
    }

    @Test
    void theWholeLogComesOutInIdentOrder() {
        List<String> lines = RoadLogFile.exportLog(seeded());
        assertThat(lines).hasSize(12);
        assertThat(lines.get(0)).startsWith("I95-1|");
        assertThat(lines).contains(LINE);
    }

    @Test
    void theLogRoundTripsThroughTheText() {
        EntityManager db = seeded();
        List<String> lines = RoadLogFile.exportLog(db);
        RoadLogFile.Loaded found = RoadLogFile.loadLog(db, lines);
        work.commit();
        assertThat(found.read()).isEqualTo(12);
        assertThat(found.added()).isEmpty();
        assertThat(found.updated()).hasSize(12);
    }

    @Test
    void aNewSegmentIsAddedAndItsNodesAreMade() {
        EntityManager db = seeded();
        RoadLogFile.Loaded found =
                RoadLogFile.loadLog(db, List.of("US201-4|US 201|anson|solon|910|Somerset|Anson|4|0"));
        work.commit();
        assertThat(work.network().segment("US201-4").getLength()).isEqualTo(Miles.of(9, 10));
        assertThat(found.added()).containsExactly("US201-4");
        assertThat(found).hasToString("1 read, 0 refused");
    }

    @Test
    void aSegmentAlreadyOnFileIsUpdatedInPlace() {
        EntityManager db = seeded();
        RoadLogFile.Loaded found =
                RoadLogFile.loadLog(db, List.of("US201-2|US 201|fairfield|skowhegan|1300|Somerset|Fairfield|3|1"));
        work.commit();
        SegmentRow row = work.network().segment("US201-2");
        assertThat(found.updated()).containsExactly("US201-2");
        assertThat(row.getLength()).isEqualTo(Miles.of(13));
        assertThat(row.isOneWay()).isTrue();
    }

    @Test
    void aPartialLoadLeavesEverythingItDidNotMention() {
        EntityManager db = seeded();
        RoadLogFile.loadLog(db, List.of("US201-2|US 201|fairfield|skowhegan|1300|Somerset|Fairfield|3|0"));
        work.commit();
        assertThat(work.network().segments()).hasSize(12);
    }

    @Test
    void aBadLineIsRefusedByNumberAndTheRestStillLoads() {
        EntityManager db = seeded();
        RoadLogFile.Loaded found = RoadLogFile.loadLog(
                db,
                List.of(
                        "US201-4|US 201|anson|solon|910|Somerset|Anson|4|0",
                        "this line is nonsense",
                        "US201-5|US 201|solon|bingham|1120|Somerset|Solon|4|0"));
        work.commit();
        assertThat(found.added()).containsExactly("US201-4", "US201-5");
        assertThat(found.refused().get(0).line()).isEqualTo(2);
        assertThat(found.refused().get(0).why()).contains("9 fields");
    }

    @Test
    void blankLinesAndCommentsAreSkippedWithoutComplaint() {
        RoadLogFile.Loaded found = RoadLogFile.loadLog(seeded(), List.of("", "# the state writes a header", LINE));
        assertThat(found.refused()).isEmpty();
        assertThat(found.updated()).containsExactly("US201-2");
    }

    @Test
    void theLoadPrintsASummaryAndThenTheRefusals() {
        RoadLogFile.Loaded found = RoadLogFile.loadLog(seeded(), List.of("nonsense"));
        assertThat(found.lines().get(0)).isEqualTo("0 added  0 updated  1 refused");
        assertThat(found.lines().get(1)).startsWith("line 1  ");
    }
}
