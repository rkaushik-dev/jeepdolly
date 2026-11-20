package io.github.rkaushikdev.jeepdolly.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.enforcement.Offence;
import io.github.rkaushikdev.jeepdolly.permits.Application;
import io.github.rkaushikdev.jeepdolly.permits.ApplicationState;
import io.github.rkaushikdev.jeepdolly.persistence.Database;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.persistence.models.AuditRow;
import io.github.rkaushikdev.jeepdolly.support.Applications;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import io.github.rkaushikdev.jeepdolly.support.Permits;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The nightly sweep, and the one thing it is allowed to change. */
class NightlyTest {

    private static final LocalDate APRIL = LocalDate.of(2025, 4, 7);
    private static final LocalDate LATER = LocalDate.of(2025, 4, 30);
    private static final String DRAFT = "25-F-000001";

    private Database factory;

    @BeforeEach
    void seed() {
        factory = Databases.seeded();
    }

    private Application filed(LocalDate when, boolean submitted) {
        Application application = Applications.application(DRAFT)
                .filed(when)
                .start(when)
                .end(when)
                .build();
        if (submitted) {
            application.submit(when);
        }
        try (UnitOfWork work = factory.unitOfWork()) {
            work.applications().add(application);
            work.commit();
        }
        return application;
    }

    private void issued(Application application) {
        try (UnitOfWork work = factory.unitOfWork()) {
            work.permits().add(Permits.aPermit(application));
            work.commit();
        }
    }

    private Nightly.Sweep sweep(LocalDate day) {
        try (UnitOfWork work = factory.unitOfWork()) {
            return Nightly.sweep(work.entityManager(), day);
        }
    }

    private void fileTicket(UnitOfWork work) {
        Enforcement.fileCitation(
                work,
                "E-25-0417",
                1204471,
                "Sgt A Thibodeau",
                Offence.NO_PERMIT,
                "US201-2",
                LocalDate.of(2025, 4, 8),
                LocalDate.of(2025, 4, 8),
                "M Cyr");
        work.commit();
    }

    @Test
    void aQuietNightChangesNothingAndSaysSo() {
        Nightly.Sweep found = sweep(APRIL);
        assertThat(found.changed()).isZero();
        assertThat(found.lines().get(found.lines().size() - 1))
                .isEqualTo("0 expiring  0 withdrawn  0 slow  0 lapsing  0 cards  0 held");
    }

    @Test
    void aPermitAboutToRunOutIsNoticedAndNotTouched() {
        issued(filed(APRIL, false));
        Nightly.Sweep found;
        try (UnitOfWork work = factory.unitOfWork()) {
            found = Nightly.sweep(work.entityManager(), APRIL, 2);
            assertThat(work.permits().row("25-P-004471").getState().value()).isEqualTo("issued");
        }
        assertThat(found.expiring()).containsExactly("25-P-004471");
        assertThat(found.lines()).contains("expiring  25-P-004471");
    }

    @Test
    void aPermitThatRanOutYesterdayIsNotExpiringAnyMore() {
        issued(filed(APRIL, false));
        assertThat(sweep(LATER).expiring()).isEmpty();
    }

    @Test
    void aDraftNobodySubmittedIsWithdrawnAfterAFortnight() {
        filed(LocalDate.of(2025, 4, 1), false);
        Nightly.Sweep found;
        try (UnitOfWork work = factory.unitOfWork()) {
            found = Nightly.sweep(work.entityManager(), LocalDate.of(2025, 4, 20));
            work.commit();
            assertThat(work.applications().row(DRAFT).getState()).isSameAs(ApplicationState.WITHDRAWN);
        }
        assertThat(found.withdrawn()).containsExactly(DRAFT);
        assertThat(found.changed()).isEqualTo(1);
    }

    @Test
    void aDraftThatIsStillFreshIsLeftAlone() {
        filed(LocalDate.of(2025, 4, 1), false);
        Nightly.Sweep found;
        try (UnitOfWork work = factory.unitOfWork()) {
            found = Nightly.sweep(work.entityManager(), LocalDate.of(2025, 4, 10));
            assertThat(work.applications().row(DRAFT).getState()).isSameAs(ApplicationState.DRAFT);
        }
        assertThat(found.withdrawn()).isEmpty();
    }

    @Test
    void aSubmittedApplicationIsNeverWithdrawnByTheNight() {
        filed(LocalDate.of(2025, 4, 1), true);
        assertThat(sweep(LocalDate.of(2025, 5, 1)).withdrawn()).isEmpty();
    }

    @Test
    void whatTheNightWithdrewLandsOnTheTrailAsTheNight() {
        filed(LocalDate.of(2025, 4, 1), false);
        List<AuditRow> trail;
        try (UnitOfWork work = factory.unitOfWork()) {
            Nightly.sweep(work.entityManager(), LocalDate.of(2025, 4, 20));
            work.commit();
            trail = Audit.forDocument(work.entityManager(), "application", DRAFT);
        }
        assertThat(trail).extracting(AuditRow::getActor).containsExactly("nightly");
        assertThat(trail.get(0).getDetail()).contains("untouched since 2025-04-01");
    }

    @Test
    void aStudyThatHasWaitedTooLongIsCountedAndNotMoved() {
        Nightly.Sweep found;
        try (UnitOfWork work = factory.unitOfWork()) {
            Studies.openStudy(work.entityManager(), "25-F-000002", List.of("1247"), LocalDate.of(2025, 4, 1));
            work.commit();
            found = Nightly.sweep(work.entityManager(), LocalDate.of(2025, 4, 30));
            assertThat(Studies.rowFor(work.entityManager(), "25-F-000002")
                            .getState()
                            .value())
                    .isEqualTo("waiting");
        }
        assertThat(found.slowStudies()).containsExactly("25-F-000002");
    }

    @Test
    void aStudyInsideTheFortnightIsNotOnTheList() {
        Nightly.Sweep found;
        try (UnitOfWork work = factory.unitOfWork()) {
            Studies.openStudy(work.entityManager(), "25-F-000002", List.of("1247"), LocalDate.of(2025, 4, 1));
            work.commit();
            found = Nightly.sweep(work.entityManager(), LocalDate.of(2025, 4, 10));
        }
        assertThat(found.slowStudies()).isEmpty();
    }

    @Test
    void insuranceLapsingInsideTheWeekIsWorthATelephoneCall() {
        Nightly.Sweep found;
        try (UnitOfWork work = factory.unitOfWork()) {
            work.carriers().row(1204471).getCertificates().get(0).setExpires(LocalDate.of(2025, 4, 10));
            work.commit();
            found = Nightly.sweep(work.entityManager(), APRIL);
        }
        assertThat(found.lapsing()).containsExactly("Bickford Heavy Haul");
        assertThat(found.lines()).contains("insurance lapsing  Bickford Heavy Haul");
    }

    @Test
    void insuranceWithMonthsLeftIsNotOnTheList() {
        assertThat(sweep(APRIL).lapsing()).isEmpty();
    }

    @Test
    void aSweepThatLooksBackwardsIsRefused() {
        try (UnitOfWork work = factory.unitOfWork()) {
            assertThatThrownBy(() -> Nightly.sweep(work.entityManager(), APRIL, -1))
                    .isInstanceOf(Impossible.class);
        }
    }

    @Test
    void theSweepPrintsAHeadingAndAFooting() {
        Nightly.Sweep found = sweep(APRIL);
        assertThat(found.lines().get(0)).isEqualTo("nightly 2025-04-07");
        assertThat(found).hasToString("nightly 2025-04-07, 0 changed");
        assertThat(APRIL.plusDays(1)).isEqualTo(LocalDate.of(2025, 4, 8));
    }

    @Test
    void aPilotCardRunningOutInsideTheMonthIsListed() {
        assertThat(sweep(LocalDate.of(2026, 4, 10)).cards()).containsExactly("C-1001 R Dubois", "C-1002 M Levesque");
    }

    @Test
    void aCardThatRanOutLongAgoIsNotListedAgain() {
        assertThat(sweep(APRIL).cards()).isEmpty();
    }

    @Test
    void nobodyIsHeldOnANightWithNoCitations() {
        assertThat(sweep(APRIL).held()).isEmpty();
    }

    @Test
    void aCarrierThatCrossedTheLineTodayIsReportedOnce() {
        Nightly.Sweep crossed;
        Nightly.Sweep after;
        try (UnitOfWork work = factory.unitOfWork()) {
            fileTicket(work);
            crossed = Nightly.sweep(work.entityManager(), LocalDate.of(2025, 5, 30));
            after = Nightly.sweep(work.entityManager(), LocalDate.of(2025, 5, 31));
        }
        assertThat(crossed.held()).containsExactly("1204471 Bickford Heavy Haul $500.00");
        assertThat(after.held()).isEmpty();
    }

    @Test
    void aHeldCarrierShowsOnThePrintedLines() {
        Nightly.Sweep found;
        try (UnitOfWork work = factory.unitOfWork()) {
            fileTicket(work);
            found = Nightly.sweep(work.entityManager(), LocalDate.of(2025, 5, 30));
        }
        assertThat(found.lines()).contains("held  1204471 Bickford Heavy Haul $500.00");
    }
}
