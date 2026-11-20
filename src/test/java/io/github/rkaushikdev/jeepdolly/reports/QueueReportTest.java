package io.github.rkaushikdev.jeepdolly.reports;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.jeepdolly.permits.Application;
import io.github.rkaushikdev.jeepdolly.persistence.Database;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.services.Studies;
import io.github.rkaushikdev.jeepdolly.support.Applications;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** How old the work is, which is the number the supervisor watches. */
class QueueReportTest {

    private static final LocalDate FILED = LocalDate.of(2025, 4, 7);
    private static final LocalDate LATER = LocalDate.of(2025, 4, 21);

    private Database factory;

    @BeforeEach
    void seed() {
        factory = Databases.seeded();
    }

    private void submitted(String reference, LocalDate when) {
        Application application = Applications.application(reference)
                .filed(when)
                .start(when)
                .end(when)
                .build();
        application.submit(when);
        try (UnitOfWork work = factory.unitOfWork()) {
            work.applications().add(application);
            work.commit();
        }
    }

    private void queued(String reference, LocalDate when) {
        try (UnitOfWork work = factory.unitOfWork()) {
            Studies.openStudy(work.entityManager(), reference, List.of("1247"), when);
            work.commit();
        }
    }

    private QueueReport.Ageing ageing(LocalDate day) {
        try (UnitOfWork work = factory.unitOfWork()) {
            return QueueReport.ageing(work.entityManager(), day);
        }
    }

    @Test
    void aQuietOfficeHasNothingWaiting() {
        QueueReport.Ageing found = ageing(FILED);
        assertThat(found.quiet()).isTrue();
        assertThat(found.oldestStudy()).isZero();
        assertThat(found.lines().get(found.lines().size() - 1)).endsWith("oldest study 0 days");
    }

    @Test
    void anApplicationInTheQueueIsAgedFromTheDayItWasFiled() {
        submitted("25-F-000001", FILED);
        QueueReport.Ageing found = ageing(LATER);
        assertThat(found.applications()).hasSize(1);
        assertThat(found.applications().get(0).days()).isEqualTo(14);
        assertThat(found.oldestApplication()).isEqualTo(14);
    }

    @Test
    void aDraftIsNotWaitingOnAnybody() {
        QueueReport.Ageing found;
        try (UnitOfWork work = factory.unitOfWork()) {
            work.applications().add(Applications.anApplication());
            work.commit();
            found = QueueReport.ageing(work.entityManager(), LATER);
        }
        assertThat(found.applications()).isEmpty();
    }

    @Test
    void aStudyWaitingAndOneWithAnEngineerAreBothStudies() {
        queued("25-F-000002", FILED);
        queued("25-F-000003", FILED);
        QueueReport.Ageing found;
        try (UnitOfWork work = factory.unitOfWork()) {
            Studies.assign(work.entityManager(), "25-F-000003", "R Pelletier", FILED);
            work.commit();
            found = QueueReport.ageing(work.entityManager(), LATER);
        }
        assertThat(found.studies())
                .extracting(QueueReport.Waiting::reference)
                .containsExactly("25-F-000002", "25-F-000003");
        assertThat(found.studies().get(1).note()).isEqualTo("R Pelletier");
    }

    @Test
    void aHeldStudyIsCountedApartFromTheEngineersQueue() {
        queued("25-F-000002", FILED);
        QueueReport.Ageing found;
        try (UnitOfWork work = factory.unitOfWork()) {
            Studies.assign(work.entityManager(), "25-F-000002", "R Pelletier", FILED);
            Studies.hold(work.entityManager(), "25-F-000002", FILED, "send the spacings");
            work.commit();
            found = QueueReport.ageing(work.entityManager(), LATER);
        }
        assertThat(found.studies()).isEmpty();
        assertThat(found.held()).hasSize(1);
        assertThat(found.held().get(0).days()).isEqualTo(14);
    }

    @Test
    void aDecidedStudyIsOffEveryQueue() {
        queued("25-F-000002", FILED);
        QueueReport.Ageing found;
        try (UnitOfWork work = factory.unitOfWork()) {
            Studies.assign(work.entityManager(), "25-F-000002", "R Pelletier", FILED);
            Studies.clear(work.entityManager(), "25-F-000002", FILED);
            work.commit();
            found = QueueReport.ageing(work.entityManager(), LATER);
        }
        assertThat(found.quiet()).isTrue();
    }

    @Test
    void whatIsOverAPromiseComesBackOldestFirst() {
        submitted("25-F-000001", LocalDate.of(2025, 4, 1));
        submitted("25-F-000004", LocalDate.of(2025, 4, 20));
        queued("25-F-000002", LocalDate.of(2025, 4, 5));
        QueueReport.Ageing found = ageing(LATER);
        assertThat(found.over(14))
                .extracting(QueueReport.Waiting::reference)
                .containsExactly("25-F-000001", "25-F-000002");
        assertThat(found.over(60)).isEmpty();
    }

    @Test
    void theReportPrintsALineForEverythingWaiting() {
        submitted("25-F-000001", FILED);
        queued("25-F-000002", FILED);
        List<String> lines = ageing(LATER).lines();
        assertThat(lines.get(0)).isEqualTo("waiting on 2025-04-21");
        assertThat(lines.get(1)).startsWith("application  25-F-000001");
        assertThat(lines.get(2)).startsWith("study  25-F-000002");
        assertThat(lines.get(lines.size() - 1)).startsWith("1 applications  1 studies");
    }
}
