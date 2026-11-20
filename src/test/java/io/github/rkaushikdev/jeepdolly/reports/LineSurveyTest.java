package io.github.rkaushikdev.jeepdolly.reports;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.persistence.repositories.LineRepository;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import io.github.rkaushikdev.jeepdolly.support.Wires;
import io.github.rkaushikdev.jeepdolly.utilities.Crossing;
import io.github.rkaushikdev.jeepdolly.utilities.LineBook;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The line survey: the van's list of low, old wires, and the wires nothing can lift. */
class LineSurveyTest {

    private static final LocalDate SUMMER = LocalDate.of(2025, 6, 1);
    private static final LocalDate LONG_AGO = LocalDate.of(2015, 6, 1);
    private static final LocalDate LAST_YEAR = LocalDate.of(2024, 5, 1);

    private UnitOfWork work;
    private EntityManager db;

    /**
     * Three wires: L-1 low and liftable, walked ten years ago; L-2 higher and fixed, walked last
     * year; L-3 the lowest, fixed, and never measured at all.
     */
    @BeforeEach
    void open() {
        work = Databases.seeded().unitOfWork();
        db = work.entityManager();
        LineBook book = Wires.aLineBook();
        LineRepository lines = work.lines();
        book.owners().forEach(lines::addOwner);
        lines.add(book.get("L-1"), LONG_AGO);
        lines.add(book.get("L-2"), LAST_YEAR);
        lines.add(new Crossing("L-3", "SR43-1", "Martin Stream", Inches.of(18), "TEL"));
        work.commit();
    }

    @AfterEach
    void close() {
        work.close();
    }

    @Test
    void everyWireComesBackLowestFirst() {
        assertThat(LineSurvey.spans(db)).extracting(LineSurvey.Span::ident).containsExactly("L-3", "L-1", "L-2");
    }

    @Test
    void onlyTheWiresUnderAHeightAreTheLowOnes() {
        assertThat(LineSurvey.spans(db, LineSurvey.LOW))
                .extracting(LineSurvey.Span::ident)
                .containsExactly("L-3", "L-1");
    }

    @Test
    void aLowWireWalkedOutOfCycleIsOverdueAndOneNeverWalkedIsToo() {
        assertThat(LineSurvey.overdue(db, SUMMER))
                .extracting(LineSurvey.Span::ident)
                .containsExactly("L-3", "L-1");
    }

    @Test
    void aFreshMeasurementTakesAWireOffTheVansList() {
        work.lines().remeasure("L-1", Inches.of(19), LAST_YEAR);
        work.commit();
        assertThat(LineSurvey.overdue(db, SUMMER))
                .extracting(LineSurvey.Span::ident)
                .containsExactly("L-3");
        assertThat(LineSurvey.overdue(db, SUMMER, LineSurvey.LOW, 1))
                .extracting(LineSurvey.Span::ident)
                .containsExactly("L-3", "L-1");
    }

    @Test
    void theFixedWiresAreTheOnesNothingCanLift() {
        assertThat(LineSurvey.fixed(db)).extracting(LineSurvey.Span::ident).containsExactly("L-3", "L-2");
    }

    @Test
    void aSpanPrintsWhereItIsHowHighAndWhenItWasWalked() {
        List<LineSurvey.Span> found = LineSurvey.spans(db);
        assertThat(found.get(1)).hasToString("L-1  US201-2 at Fairfield village  19 ft  2015-06-01");
        assertThat(found.get(0)).hasToString("L-3  SR43-1 at Martin Stream  18 ft  never  fixed");
        assertThat(found.get(1).ageDays(LocalDate.of(2015, 6, 11))).hasValue(10);
        assertThat(found.get(0).ageDays(SUMMER)).isEmpty();
        assertThat(found.get(2).staleOn(SUMMER)).isFalse();
        assertThat(found.get(0).staleOn(SUMMER)).isTrue();
    }

    @Test
    void theReportListsTheVansWorkAndThenTheFixedWires() {
        List<String> lines = LineSurvey.report(db, SUMMER);
        assertThat(lines)
                .containsExactly(
                        "line survey 2025-06-01, wires under 20 ft",
                        "L-3  SR43-1 at Martin Stream  18 ft  never  fixed",
                        "L-1  US201-2 at Fairfield village  19 ft  2015-06-01",
                        "wires that cannot be lifted",
                        "  L-3  SR43-1 at Martin Stream  18 ft  never  fixed",
                        "  L-2  I95-2 at Burnham line  24 ft  2024-05-01  fixed");
    }

    @Test
    void aSurveyWithNothingLowOutOfCycleSaysSo() {
        assertThat(LineSurvey.report(db, SUMMER, Inches.of(10)))
                .startsWith("line survey 2025-06-01, wires under 10 ft", "nothing low is out of cycle");
    }
}
