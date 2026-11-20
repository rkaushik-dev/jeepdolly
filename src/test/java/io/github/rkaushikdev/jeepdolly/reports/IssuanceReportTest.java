package io.github.rkaushikdev.jeepdolly.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.permits.Application;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The evening report, which somebody initials. */
class IssuanceReportTest {

    private static final LocalDate MONDAY = LocalDate.of(2025, 4, 7);
    private static final LocalDate TUESDAY = LocalDate.of(2025, 4, 8);

    /** One permit to write: its sequence, its series, and the day it was issued. */
    private record Written(int sequence, Series series, LocalDate issued) {}

    private Database factory;

    @BeforeEach
    void seed() {
        factory = Databases.seeded();
    }

    private void written(List<Written> numbers) {
        Application application = Applications.anApplication();
        try (UnitOfWork work = factory.unitOfWork()) {
            work.applications().add(application);
            work.commit();
            for (Written one : numbers) {
                LocalDate start = one.issued().isAfter(application.start()) ? one.issued() : application.start();
                work.permits()
                        .add(Permits.permit(application)
                                .number(new PermitNumber(2025, one.series(), one.sequence()))
                                .issued(one.issued())
                                .start(start)
                                .build());
            }
            work.commit();
        }
    }

    private IssuanceReport.Issuance issuance(LocalDate day) {
        try (UnitOfWork work = factory.unitOfWork()) {
            return IssuanceReport.issuance(work.entityManager(), day);
        }
    }

    @Test
    void aDayWithNothingOnItStillPrintsAHeading() {
        IssuanceReport.Issuance found = issuance(MONDAY);
        assertThat(found.count()).isZero();
        assertThat(found.lines().get(0)).isEqualTo("permits issued 2025-04-07");
        assertThat(found.taken()).isEqualTo(new Dollars(0));
    }

    @Test
    void theDayCountsWhatWasWrittenAndWhatItTook() {
        written(List.of(new Written(1, Series.SINGLE, MONDAY), new Written(2, Series.SINGLE, MONDAY)));
        IssuanceReport.Issuance found = issuance(MONDAY);
        assertThat(found.count()).isEqualTo(2);
        assertThat(found.taken()).isEqualTo(Dollars.of(92, 24));
        assertThat(found).hasToString("2 permits, $92.24");
    }

    @Test
    void theMoneyIsTheLinesThatWerePrintedAndNotARecomputation() {
        written(List.of(new Written(1, Series.SINGLE, MONDAY)));
        assertThat(issuance(MONDAY).permits().get(0).fee()).isEqualTo(Dollars.of(46, 12));
    }

    @Test
    void theCountBySeriesSaysWhatKindOfDayItWas() {
        written(List.of(
                new Written(1, Series.SINGLE, MONDAY),
                new Written(2, Series.SINGLE, MONDAY),
                new Written(1, Series.SUPERLOAD, MONDAY)));
        IssuanceReport.Issuance found = issuance(MONDAY);
        assertThat(found.bySeries()).isEqualTo(Map.of("P", 2, "S", 1));
        assertThat(found.ofSeries("S"))
                .extracting(IssuanceReport.Issued::number)
                .containsExactly("25-S-000001");
    }

    @Test
    void aSpanOfDaysReadsAsASpan() {
        written(List.of(new Written(1, Series.SINGLE, MONDAY), new Written(2, Series.SINGLE, TUESDAY)));
        IssuanceReport.Issuance oneDay = issuance(MONDAY);
        IssuanceReport.Issuance both;
        try (UnitOfWork work = factory.unitOfWork()) {
            both = IssuanceReport.issuance(work.entityManager(), MONDAY, TUESDAY);
        }
        assertThat(oneDay.count()).isEqualTo(1);
        assertThat(both.count()).isEqualTo(2);
        assertThat(both.lines().get(0)).isEqualTo("permits issued 2025-04-07 to 2025-04-08");
        assertThat(both.oneDay()).isFalse();
    }

    @Test
    void theReportLinesAreWhatGetsInitialled() {
        written(List.of(new Written(1, Series.SINGLE, MONDAY)));
        List<String> lines = issuance(MONDAY).lines();
        assertThat(lines.get(1)).isEqualTo("25-P-000001  Bickford Heavy Haul  94,000 lb  $46.12");
        assertThat(lines.get(lines.size() - 1)).isEqualTo("1 permits  $46.12");
    }

    @Test
    void theRefusedAndWithdrawnAreCountedWhereThereAreAny() {
        Application application = Applications.anApplication();
        application.submit(MONDAY);
        application.refuse(MONDAY, "the bridge is posted");
        IssuanceReport.Issuance found;
        try (UnitOfWork work = factory.unitOfWork()) {
            work.applications().add(application);
            work.commit();
            found = IssuanceReport.issuance(work.entityManager(), MONDAY);
        }
        assertThat(found.refused()).isEqualTo(1);
        assertThat(found.withdrawn()).isZero();
        assertThat(found.lines()).contains("1 refused  0 withdrawn");
    }

    @Test
    void aReportThatEndsBeforeItStartsIsRefused() {
        try (UnitOfWork work = factory.unitOfWork()) {
            assertThatThrownBy(() -> IssuanceReport.issuance(work.entityManager(), TUESDAY, MONDAY))
                    .isInstanceOf(Impossible.class);
        }
    }
}
