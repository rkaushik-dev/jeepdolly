package io.github.rkaushikdev.jeepdolly.services;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.jeepdolly.permits.Application;
import io.github.rkaushikdev.jeepdolly.permits.ApplicationState;
import io.github.rkaushikdev.jeepdolly.permits.Permit;
import io.github.rkaushikdev.jeepdolly.persistence.Database;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.support.Applications;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import io.github.rkaushikdev.jeepdolly.support.Permits;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The numbers about the office, counted off the database every time. */
class MetricsTest {

    private static final LocalDate APRIL = LocalDate.of(2025, 4, 7);
    private static final LocalDate START = LocalDate.of(2025, 4, 8);

    private Database factory;

    @BeforeEach
    void seed() {
        factory = Databases.seeded();
    }

    private void issued() {
        Application application = Applications.anApplication();
        try (UnitOfWork work = factory.unitOfWork()) {
            work.applications().add(application);
            work.commit();
            work.permits().add(Permits.aPermit(application));
            work.commit();
        }
    }

    private Map<String, Long> numbers() {
        return numbers(START);
    }

    private Map<String, Long> numbers(LocalDate day) {
        try (UnitOfWork work = factory.unitOfWork()) {
            return Metrics.byName(work.entityManager(), day);
        }
    }

    @Test
    void anEmptyOfficeStillCountsTheRoadLog() {
        Map<String, Long> found = numbers();
        assertThat(found.get("permits_on_the_road")).isZero();
        assertThat(found.get("segments")).isEqualTo(12);
        assertThat(found.get("structures_posted")).isEqualTo(1);
        assertThat(found.get("carriers")).isEqualTo(2);
    }

    @Test
    void aPermitShowsUpOnTheRoadAndInTheWeek() {
        issued();
        Map<String, Long> found = numbers();
        assertThat(found.get("permits_on_the_road")).isEqualTo(1);
        assertThat(found.get("permits_issued_week")).isEqualTo(1);
        assertThat(found.get("permits_void")).isZero();
    }

    @Test
    void aPermitThatHasRunOutIsOffTheRoadButStillInTheWeek() {
        issued();
        Map<String, Long> found = numbers(LocalDate.of(2025, 4, 12));
        assertThat(found.get("permits_on_the_road")).isZero();
        assertThat(found.get("permits_issued_week")).isEqualTo(1);
    }

    @Test
    void aVoidedPermitMovesFromOneNumberToAnother() {
        issued();
        try (UnitOfWork work = factory.unitOfWork()) {
            Permit permit = work.permits().get("25-P-004471");
            permit.makeVoid(START, "load cancelled");
            work.permits().saveState(permit);
            work.commit();
        }
        Map<String, Long> found = numbers();
        assertThat(found.get("permits_on_the_road")).isZero();
        assertThat(found.get("permits_void")).isEqualTo(1);
    }

    @Test
    void theQueuesAreCountedByState() {
        try (UnitOfWork work = factory.unitOfWork()) {
            work.applications().add(Applications.anApplication());
            work.commit();
        }
        assertThat(numbers().get("applications_draft")).isEqualTo(1);
        assertThat(numbers().get("applications_waiting")).isZero();
        try (UnitOfWork work = factory.unitOfWork()) {
            work.applications().row("25-04471").setState(ApplicationState.SUBMITTED);
            work.commit();
        }
        assertThat(numbers().get("applications_waiting")).isEqualTo(1);
    }

    @Test
    void anOpenStudyIsCountedAndADecidedOneIsNot() {
        try (UnitOfWork work = factory.unitOfWork()) {
            Studies.openStudy(work.entityManager(), "25-F-000002", List.of("1247"), APRIL);
            work.commit();
        }
        assertThat(numbers().get("studies_open")).isEqualTo(1);
        try (UnitOfWork work = factory.unitOfWork()) {
            Studies.assign(work.entityManager(), "25-F-000002", "R Pelletier", APRIL);
            Studies.clear(work.entityManager(), "25-F-000002", START);
            work.commit();
        }
        assertThat(numbers().get("studies_open")).isZero();
    }

    @Test
    void theExpositionCarriesAHelpAndATypeForEveryNumber() {
        String written;
        try (UnitOfWork work = factory.unitOfWork()) {
            written = Metrics.exposition(work.entityManager(), START);
        }
        assertThat(written).contains("# HELP jeepdolly_segments segments on the road log");
        assertThat(written).contains("# TYPE jeepdolly_segments gauge");
        assertThat(written).contains("jeepdolly_segments 12");
        assertThat(written).endsWith("\n");
    }

    @Test
    void everyNameIsFlatAndPrefixed() {
        List<Metrics.Measure> found;
        try (UnitOfWork work = factory.unitOfWork()) {
            found = Metrics.snapshot(work.entityManager(), START);
        }
        assertThat(found).isNotEmpty().allSatisfy(one -> {
            assertThat(one.toString()).startsWith("jeepdolly_");
            assertThat(one.name()).doesNotContain(" ");
            assertThat(one.about()).isNotEmpty();
        });
    }
}
