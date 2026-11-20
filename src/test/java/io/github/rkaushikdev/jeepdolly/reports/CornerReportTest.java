package io.github.rkaushikdev.jeepdolly.reports;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import io.github.rkaushikdev.jeepdolly.permits.Application;
import io.github.rkaushikdev.jeepdolly.persistence.Books;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.routing.Corner;
import io.github.rkaushikdev.jeepdolly.routing.Itinerary;
import io.github.rkaushikdev.jeepdolly.routing.TurnBook;
import io.github.rkaushikdev.jeepdolly.support.Applications;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import io.github.rkaushikdev.jeepdolly.support.Permits;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The corner list, on the wall and along one route. */
class CornerReportTest {

    private static final LocalDate FIRST = LocalDate.of(2025, 1, 1);
    private static final LocalDate LAST = LocalDate.of(2025, 12, 31);
    private static final List<String> ACROSS = List.of("US201-1", "US201-2", "US2-1", "SA148-1", "SR43-1");
    private static final Corner SQUARE = new Corner("skowhegan", "US201-2", "US2-1", 55);

    private UnitOfWork work;
    private EntityManager db;

    @BeforeEach
    void open() {
        work = Databases.seeded().unitOfWork();
        db = work.entityManager();
    }

    @AfterEach
    void close() {
        work.close();
    }

    private static Envelope load() {
        return load("80'");
    }

    private static Envelope load(String length) {
        return Envelope.read("12'", "13'6", length, "0", "0");
    }

    private Itinerary trip() {
        return trip(ACROSS);
    }

    private Itinerary trip(List<String> idents) {
        return new Itinerary(Books.networkFrom(db), "waterville", idents);
    }

    private void aWrittenPermit() {
        aWrittenPermit(ACROSS);
    }

    private void aWrittenPermit(List<String> route) {
        Application application = Applications.anApplication();
        work.applications().add(application);
        db.flush();
        work.permits().add(Permits.permit(application).route(route).build());
        db.flush();
    }

    @Test
    void theWallListIsTheTightCornersAndNothingElse() {
        assertThat(CornerReport.wallList(db, TurnBook.coded(), FIRST, LAST))
                .extracting(CornerReport.Wall::node)
                .containsExactly("madison", "norridgewock");
    }

    @Test
    void theWallListIsTightestFirstBecauseThatIsHowItIsRead() {
        assertThat(CornerReport.wallList(db, TurnBook.coded(), FIRST, LAST))
                .extracting(one -> one.corner().radiusFeet())
                .isSorted();
    }

    @Test
    void aWiderRadiusAskedForPutsMoreCornersOnTheList() {
        assertThat(CornerReport.wallList(db, TurnBook.coded(), FIRST, LAST, 70)).hasSizeGreaterThan(2);
    }

    @Test
    void aCardIndexWithNothingTightInItGivesAnEmptyList() {
        TurnBook book = new TurnBook();
        book.add(new Corner("skowhegan", "US201-2", "US2-1", 200));
        assertThat(CornerReport.wallList(db, book, FIRST, LAST)).isEmpty();
    }

    @Test
    void nothingHasTurnedAtACornerOnAQuietYear() {
        assertThat(CornerReport.turnedAt(db, SQUARE, FIRST, LAST)).isZero();
    }

    @Test
    void aPermitOverBothSegmentsCountsAtTheCorner() {
        aWrittenPermit();
        assertThat(CornerReport.turnedAt(db, SQUARE, FIRST, LAST)).isEqualTo(1);
    }

    @Test
    void aPermitThatMissesOneOfTheTwoSegmentsDoesNotCount() {
        aWrittenPermit(List.of("US201-1", "US201-2"));
        assertThat(CornerReport.turnedAt(db, SQUARE, FIRST, LAST)).isZero();
    }

    @Test
    void aPermitOutsideTheDaysAskedAboutDoesNotCount() {
        aWrittenPermit();
        assertThat(CornerReport.turnedAt(db, SQUARE, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)))
                .isZero();
    }

    @Test
    void theTrafficShowsUpOnTheWallList() {
        aWrittenPermit();
        CornerReport.Wall square = CornerReport.wallList(db, TurnBook.coded(), FIRST, LAST, 80).stream()
                .filter(one -> one.corner().outOf().equals("US2-1"))
                .findFirst()
                .orElseThrow();
        assertThat(square.turned()).isEqualTo(1);
        assertThat(square.toString()).contains("1 permits");
    }

    @Test
    void aCornerNothingTurnedAtSaysSoInWords() {
        assertThat(CornerReport.wallList(db, TurnBook.coded(), FIRST, LAST)
                        .get(0)
                        .toString())
                .contains("nothing this year");
    }

    @Test
    void thePrintedWallListHasAHeadingAndALineEach() {
        List<String> lines = CornerReport.report(db, TurnBook.coded(), FIRST, LAST);
        assertThat(lines.get(0)).isEqualTo("corners on the wall 2025-01-01 to 2025-12-31");
        assertThat(lines).hasSize(3);
    }

    @Test
    void anEmptyCardIndexPrintsASentenceRatherThanNothing() {
        assertThat(CornerReport.report(db, new TurnBook(), FIRST, LAST).get(1))
                .isEqualTo("nothing on the card index is under the tight radius");
    }

    @Test
    void everyCornerOnTheRouteComesBackWithAVerdict() {
        List<CornerReport.OnRoute> found = CornerReport.along(trip(), load(), TurnBook.coded());
        assertThat(found).extracting(one -> one.corner().node()).containsExactly("skowhegan", "norridgewock", "canaan");
        assertThat(found).extracting(CornerReport.OnRoute::verdict).containsOnly("clear");
    }

    @Test
    void aLoadThatOnlyJustGetsRoundIsMarkedClose() {
        List<CornerReport.OnRoute> close = CornerReport.along(trip(), load("95'"), TurnBook.coded()).stream()
                .filter(one -> one.verdict().equals("close"))
                .toList();
        assertThat(close).extracting(one -> one.corner().node()).containsExactly("skowhegan");
        assertThat(close.get(0).spare()).isEqualTo(5);
    }

    @Test
    void aLoadThatWillNotGetRoundIsMarkedSo() {
        List<CornerReport.OnRoute> found = CornerReport.along(trip(), load("150'"), TurnBook.coded());
        assertThat(found).extracting(CornerReport.OnRoute::verdict).containsOnly("will not get round");
        assertThat(found.get(0).spare()).isNegative();
    }

    @Test
    void theRouteReportCountsWhatStopsTheLoad() {
        List<String> lines = CornerReport.routeReport(trip(), load("150'"), TurnBook.coded());
        assertThat(lines.get(0)).isEqualTo("corners on waterville to pittsfield");
        assertThat(lines.get(lines.size() - 1)).isEqualTo("this load is stopped at 3 of them");
    }

    @Test
    void aRouteWithNoRecordedCornerSaysSo() {
        List<String> lines = CornerReport.routeReport(trip(List.of("I95-1", "I95-2")), load(), new TurnBook());
        assertThat(lines.get(lines.size() - 1)).isEqualTo("no corner on this route is on the card index");
    }

    @Test
    void aClearRouteReportNamesNoCountOfTrouble() {
        assertThat(CornerReport.routeReport(trip(), load(), TurnBook.coded()))
                .noneSatisfy(one -> assertThat(one).contains("stopped at"));
    }
}
