package io.github.rkaushikdev.jeepdolly.reports;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.permits.Application;
import io.github.rkaushikdev.jeepdolly.permits.Permit;
import io.github.rkaushikdev.jeepdolly.permits.PermitNumber;
import io.github.rkaushikdev.jeepdolly.permits.Series;
import io.github.rkaushikdev.jeepdolly.persistence.Database;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.support.Applications;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import io.github.rkaushikdev.jeepdolly.support.Permits;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** What actually crossed each structure, which is the record nobody else has. */
class StructureReportTest {

    private static final LocalDate MONDAY = LocalDate.of(2025, 4, 7);
    private static final LocalDate FRIDAY = LocalDate.of(2025, 4, 11);
    private static final List<String> ACROSS = List.of("US201-1", "US201-2", "SA148-2", "SR43-1");
    private static final List<String> OVER = List.of("US201-1", "US201-2", "US2-1", "SA148-1", "SR43-1");

    /** One permit to write: its sequence, its route, and its gross weight. */
    private record Written(int sequence, List<String> route, int gross) {}

    private Database factory;

    @BeforeEach
    void seed() {
        factory = Databases.seeded();
    }

    private void written(List<Written> permits) {
        Application application = Applications.anApplication();
        try (UnitOfWork work = factory.unitOfWork()) {
            work.applications().add(application);
            work.commit();
            for (Written one : permits) {
                work.permits()
                        .add(Permits.permit(application)
                                .number(new PermitNumber(2025, Series.SINGLE, one.sequence()))
                                .route(one.route())
                                .gross(new Pounds(one.gross()))
                                .build());
            }
            work.commit();
        }
    }

    private Map<String, StructureReport.Hits> crossings(LocalDate start, LocalDate end) {
        try (UnitOfWork work = factory.unitOfWork()) {
            return StructureReport.crossings(work.entityManager(), start, end).stream()
                    .collect(Collectors.toMap(StructureReport.Hits::ident, Function.identity()));
        }
    }

    @Test
    void nothingCrossedAnythingOnAQuietWeek() {
        List<String> lines;
        try (UnitOfWork work = factory.unitOfWork()) {
            lines = StructureReport.report(work.entityManager(), MONDAY, FRIDAY);
        }
        assertThat(lines.get(lines.size() - 1)).isEqualTo("nothing crossed anything");
    }

    @Test
    void aPermitIsCountedOntoEveryStructureOnItsRoute() {
        written(List.of(new Written(1, ACROSS, 94000)));
        Map<String, StructureReport.Hits> found = crossings(MONDAY, FRIDAY);
        assertThat(found.get("1247").count()).isEqualTo(1);
        assertThat(found.get("3310").count()).isEqualTo(1);
        assertThat(found.get("5006").count()).isZero();
    }

    @Test
    void theHeaviestCrossingIsTheNumberTheBridgePeopleWant() {
        written(List.of(new Written(1, ACROSS, 94000), new Written(2, ACROSS, 132000)));
        StructureReport.Crossing heaviest =
                crossings(MONDAY, FRIDAY).get("1247").heaviest().orElseThrow();
        assertThat(heaviest.gross()).isEqualTo(new Pounds(132000));
        assertThat(heaviest.permit()).isEqualTo("25-P-000002");
    }

    @Test
    void aVoidedPermitIsNotCountedBecauseItDidNotGo() {
        written(List.of(new Written(1, ACROSS, 94000)));
        try (UnitOfWork work = factory.unitOfWork()) {
            Permit permit = work.permits().get("25-P-000001");
            permit.makeVoid(MONDAY, "load cancelled");
            work.permits().saveState(permit);
            work.commit();
        }
        assertThat(crossings(MONDAY, FRIDAY).get("1247").count()).isZero();
    }

    @Test
    void aPermitOutsideTheSpanIsNotCounted() {
        written(List.of(new Written(1, ACROSS, 94000)));
        assertThat(crossings(LocalDate.of(2025, 5, 1), LocalDate.of(2025, 5, 9))
                        .get("1247")
                        .count())
                .isZero();
    }

    @Test
    void theBusiestStructuresAreWhereTheInspectionGoes() {
        written(List.of(new Written(1, ACROSS, 94000), new Written(2, List.of("US201-1", "US201-2"), 80000)));
        List<StructureReport.Hits> found;
        try (UnitOfWork work = factory.unitOfWork()) {
            found = StructureReport.busiest(work.entityManager(), MONDAY, FRIDAY);
        }
        assertThat(found.get(0).ident()).isEqualTo("1247");
        assertThat(found.get(0).count()).isEqualTo(2);
        assertThat(found).allSatisfy(one -> assertThat(one.count()).isPositive());
    }

    @Test
    void anythingPermittedOverAPostingIsReportedLoudly() {
        written(List.of(new Written(1, OVER, 94000)));
        List<StructureReport.Hits> found;
        try (UnitOfWork work = factory.unitOfWork()) {
            found = StructureReport.anythingOverAPosting(work.entityManager(), MONDAY, FRIDAY);
        }
        assertThat(found).extracting(StructureReport.Hits::ident).containsExactly("1533");
        assertThat(found.get(0).overPosting().get(0).gross()).isEqualTo(new Pounds(94000));
    }

    @Test
    void aLoadInsideThePostingIsNotReported() {
        written(List.of(new Written(1, OVER, 40000)));
        try (UnitOfWork work = factory.unitOfWork()) {
            assertThat(StructureReport.anythingOverAPosting(work.entityManager(), MONDAY, FRIDAY))
                    .isEmpty();
        }
    }

    @Test
    void theOverThePostingLineShouts() {
        written(List.of(new Written(1, OVER, 94000)));
        List<String> lines;
        try (UnitOfWork work = factory.unitOfWork()) {
            lines = StructureReport.report(work.entityManager(), MONDAY, FRIDAY);
        }
        assertThat(lines).anySatisfy(one -> assertThat(one).contains("OVER THE POSTING"));
        assertThat(lines.get(0)).startsWith("structure crossings 2025-04-07");
    }
}
