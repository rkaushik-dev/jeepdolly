package io.github.rkaushikdev.jeepdolly.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.jeepdolly.NotAllowed;
import io.github.rkaushikdev.jeepdolly.api.Deps;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import io.github.rkaushikdev.jeepdolly.permits.Application;
import io.github.rkaushikdev.jeepdolly.permits.ApplicationState;
import io.github.rkaushikdev.jeepdolly.permits.Assessment;
import io.github.rkaushikdev.jeepdolly.persistence.Database;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.persistence.models.AuditRow;
import io.github.rkaushikdev.jeepdolly.support.Applications;
import io.github.rkaushikdev.jeepdolly.support.Combinations;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import io.github.rkaushikdev.jeepdolly.support.Streams;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The counter sequence, in one place instead of four. */
class CounterTest {

    private static final LocalDate FILED = LocalDate.of(2025, 4, 7);
    private static final String CLERK = "D Bickford";
    private static final String REFERENCE = "25-04471";

    private Database factory;

    @BeforeEach
    void seed() {
        factory = Databases.seeded();
    }

    private static Application application() {
        return Applications.anApplication(REFERENCE);
    }

    private static Application gravel() {
        return Applications.application(REFERENCE).commodity("GRVL").build();
    }

    @Test
    void aReferenceComesOffTheSameCounterAsAPermitNumber() {
        try (UnitOfWork work = factory.unitOfWork()) {
            assertThat(Counter.nextReference(work, FILED)).isEqualTo("25-F-000001");
            assertThat(Counter.nextReference(work, FILED)).isEqualTo("25-F-000002");
            work.commit();
        }
    }

    @Test
    void filingWritesTheApplicationAndTheTrailLine() {
        List<AuditRow> found;
        try (UnitOfWork work = factory.unitOfWork()) {
            Counter.fileApplication(work, application(), CLERK, FILED);
            work.commit();
            found = Audit.forDocument(work.entityManager(), "application", REFERENCE);
            assertThat(work.applications().row(REFERENCE).getState()).isSameAs(ApplicationState.DRAFT);
        }
        assertThat(found).extracting(AuditRow::getAction).containsExactly("application.filed");
        assertThat(found.get(0).getDetail()).isEqualTo("waterville to pittsfield");
    }

    @Test
    void submittingMovesItOnAndWritesItsOwnLine() {
        try (UnitOfWork work = factory.unitOfWork()) {
            Counter.fileApplication(work, application(), CLERK, FILED);
            work.flush();
            Counter.submit(work, REFERENCE, CLERK, FILED);
            work.commit();
            assertThat(work.applications().row(REFERENCE).getState()).isSameAs(ApplicationState.SUBMITTED);
            assertThat(Audit.forDocument(work.entityManager(), "application", REFERENCE))
                    .hasSize(2);
        }
    }

    @Test
    void aWalkInIsFiledSubmittedAndIssuedInOneGo() {
        Counter.Written written;
        try (UnitOfWork work = factory.unitOfWork()) {
            written = Counter.overTheCounter(work, Deps.officeFrom(work, FILED), application(), CLERK, FILED);
            work.commit();
            assertThat(work.applications().row(REFERENCE).getState()).isSameAs(ApplicationState.ISSUED);
        }
        assertThat(written.number()).isEqualTo("25-P-000001");
        assertThat(written.assessment().permittable()).isTrue();
    }

    @Test
    void theWalkInLeavesThreeLinesOnTheTrail() {
        List<AuditRow> applied;
        List<AuditRow> permit;
        try (UnitOfWork work = factory.unitOfWork()) {
            Counter.overTheCounter(work, Deps.officeFrom(work, FILED), application(), CLERK, FILED);
            work.commit();
            applied = Audit.forDocument(work.entityManager(), "application", REFERENCE);
            permit = Audit.forDocument(work.entityManager(), "permit", "25-P-000001");
        }
        assertThat(applied)
                .extracting(AuditRow::getAction)
                .containsExactly("application.filed", "application.submitted");
        assertThat(permit).extracting(AuditRow::getAction).containsExactly("permit.issued");
    }

    @Test
    void aWalkInThatCannotBePermittedRefusesTheWholeThing() {
        NotAllowed refusal;
        try (UnitOfWork work = factory.unitOfWork()) {
            refusal = catchThrowableOfType(
                    NotAllowed.class,
                    () -> Counter.overTheCounter(work, Deps.officeFrom(work, FILED), gravel(), CLERK, FILED));
            work.commit();
            assertThat(work.applications().row(REFERENCE).getState()).isSameAs(ApplicationState.REFUSED);
        }
        assertThat(refusal.facts().get("reference")).isEqualTo(REFERENCE);
        assertThat((List<?>) refusal.facts().get("reasons")).isNotEmpty();
    }

    @Test
    void aRefusalIsWrittenDownWithTheFirstReason() {
        List<AuditRow> found;
        try (UnitOfWork work = factory.unitOfWork()) {
            catchThrowableOfType(
                    NotAllowed.class,
                    () -> Counter.overTheCounter(work, Deps.officeFrom(work, FILED), gravel(), CLERK, FILED));
            work.commit();
            found = Audit.forDocument(work.entityManager(), "application", REFERENCE);
        }
        AuditRow last = found.get(found.size() - 1);
        assertThat(last.getAction()).isEqualTo("application.refused");
        assertThat(last.getDetail()).contains("loaded to legal weight");
    }

    @Test
    void aPermitIsAssessedForTheDayItTravelsAndNotForToday() {
        try (UnitOfWork work = factory.unitOfWork()) {
            Assessment found = Counter.lookAt(Deps.officeFrom(work, FILED), application());
            assertThat(found.permittable()).isTrue();
            Assessment later = Counter.lookAt(Deps.officeFrom(work, FILED), application(), LocalDate.of(2025, 4, 9));
            assertThat(later.permittable()).isTrue();
        }
    }

    @Test
    void voidingGoesThroughTheCounterToo() {
        List<AuditRow> found;
        try (UnitOfWork work = factory.unitOfWork()) {
            Counter.overTheCounter(work, Deps.officeFrom(work, FILED), application(), CLERK, FILED);
            work.commit();
            Counter.voidPermit(work, "25-P-000001", CLERK, FILED, "load cancelled");
            work.commit();
            assertThat(work.permits().row("25-P-000001").getState().value()).isEqualTo("void");
            found = Audit.forDocument(work.entityManager(), "permit", "25-P-000001");
        }
        assertThat(found).extracting(AuditRow::getAction).containsExactly("permit.issued", "permit.voided");
    }

    @Test
    void aWideLoadStillWalksTheWholeProcedure() {
        Application wide = Applications.application(REFERENCE)
                .combination(Combinations.aCombination(
                        Streams.tractorLowboy(), Envelope.read("15'", "13'6", "82'", "0", "6'")))
                .route(List.of("US201-1", "US201-2", "SA148-2", "SR43-1"))
                .build();
        Counter.Written written;
        try (UnitOfWork work = factory.unitOfWork()) {
            written = Counter.overTheCounter(work, Deps.officeFrom(work, FILED), wide, CLERK, FILED);
            work.commit();
        }
        assertThat(written.permit().conditionCodes()).contains("E-01");
        assertThat(written.assessment().escorts()).isPresent();
    }
}
