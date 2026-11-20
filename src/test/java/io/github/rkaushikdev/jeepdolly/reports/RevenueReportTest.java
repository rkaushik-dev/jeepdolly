package io.github.rkaushikdev.jeepdolly.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.fees.FeeLine;
import io.github.rkaushikdev.jeepdolly.fees.Quote;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Where the money came from, cut three ways. */
class RevenueReportTest {

    private static final LocalDate APRIL = LocalDate.of(2025, 4, 7);
    private static final LocalDate MAY = LocalDate.of(2025, 5, 6);

    /** One permit to write: its sequence, the day it was issued, and the lines it was charged. */
    private record Written(int sequence, LocalDate issued, List<FeeLine> lines) {}

    private Database factory;

    @BeforeEach
    void seed() {
        factory = Databases.seeded();
    }

    private static FeeLine line(String description, Dollars amount) {
        return new FeeLine(description, amount);
    }

    private static LocalDate later(LocalDate one, LocalDate other) {
        return one.isAfter(other) ? one : other;
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
                                .issued(one.issued())
                                .start(later(one.issued(), application.start()))
                                .end(later(one.issued(), application.end()))
                                .quote(new Quote(one.lines(), "schedule of 2024-01-01"))
                                .build());
            }
            work.commit();
        }
    }

    private RevenueReport.Revenue revenue() {
        try (UnitOfWork work = factory.unitOfWork()) {
            return RevenueReport.revenue(work.entityManager(), APRIL, MAY);
        }
    }

    @Test
    void aLineIsGroupedByWhatItSaysItIs() {
        assertThat(RevenueReport.kindOf("ordinary permit")).isEqualTo("base fee");
        assertThat(RevenueReport.kindOf("34 miles on arterial")).isEqualTo("mileage");
        assertThat(RevenueReport.kindOf("25,000 lb over legal gross")).isEqualTo("weight");
        assertThat(RevenueReport.kindOf("engineering review")).isEqualTo("review");
        assertThat(RevenueReport.kindOf("minimum fee adjustment")).isEqualTo("minimum");
        assertThat(RevenueReport.kindOf("something nobody named")).isEqualTo("other");
    }

    @Test
    void theKindsAddUpToWhatWasTaken() {
        written(List.of(new Written(
                1,
                APRIL,
                List.of(line("ordinary permit", Dollars.of(40)), line("34 miles on arterial", Dollars.of(6))))));
        RevenueReport.Revenue found = revenue();
        assertThat(found.kind("base fee")).isEqualTo(Dollars.of(40));
        assertThat(found.kind("mileage")).isEqualTo(Dollars.of(6));
        assertThat(found.taken()).isEqualTo(Dollars.of(46));
        assertThat(found.permits()).isEqualTo(1);
    }

    @Test
    void aKindNobodyChargedIsNothingRatherThanMissing() {
        written(List.of(new Written(1, APRIL, List.of(line("ordinary permit", Dollars.of(40))))));
        assertThat(revenue().kind("weight")).isEqualTo(new Dollars(0));
    }

    @Test
    void theMonthsAreKeptApartForTheBudget() {
        written(List.of(
                new Written(1, APRIL, List.of(line("ordinary permit", Dollars.of(40)))),
                new Written(2, MAY, List.of(line("ordinary permit", Dollars.of(40))))));
        assertThat(revenue().byMonth()).isEqualTo(Map.of("2025-04", Dollars.of(40), "2025-05", Dollars.of(40)));
    }

    @Test
    void aCarrierCanBeHandedItsOwnTotal() {
        written(List.of(new Written(1, APRIL, List.of(line("ordinary permit", Dollars.of(40))))));
        try (UnitOfWork work = factory.unitOfWork()) {
            assertThat(RevenueReport.owedBy(work.entityManager(), "Bickford Heavy Haul", APRIL, MAY))
                    .isEqualTo(Dollars.of(40));
            assertThat(RevenueReport.owedBy(work.entityManager(), "Somebody Else", APRIL, MAY))
                    .isEqualTo(new Dollars(0));
        }
    }

    @Test
    void aVoidedPermitIsStillCountedBecauseTheMoneyWasTaken() {
        written(List.of(new Written(1, APRIL, List.of(line("ordinary permit", Dollars.of(40))))));
        RevenueReport.Revenue found;
        try (UnitOfWork work = factory.unitOfWork()) {
            Permit permit = work.permits().get("25-P-000001");
            permit.makeVoid(APRIL, "load cancelled");
            work.permits().saveState(permit);
            work.commit();
            found = RevenueReport.revenue(work.entityManager(), APRIL, MAY);
        }
        assertThat(found.taken()).isEqualTo(Dollars.of(40));
        assertThat(found.lines().get(found.lines().size() - 1)).contains("voided permits are counted");
    }

    @Test
    void theReportPrintsTheKindsInAStableOrder() {
        written(List.of(new Written(
                1,
                APRIL,
                List.of(
                        line("ordinary permit", Dollars.of(40)),
                        line("25,000 lb over legal gross", Dollars.of(140))))));
        List<String> lines = revenue().lines();
        assertThat(lines.get(1)).isEqualTo("base fee  $40.00");
        assertThat(lines.get(2)).isEqualTo("weight  $140.00");
        assertThat(lines.get(3)).isEqualTo("1 permits  $180.00");
    }

    @Test
    void aSpanThatEndsBeforeItStartsIsRefused() {
        try (UnitOfWork work = factory.unitOfWork()) {
            assertThatThrownBy(() -> RevenueReport.revenue(work.entityManager(), MAY, APRIL))
                    .isInstanceOf(Impossible.class);
        }
    }

    @Test
    void aSpanWithNothingInItTakesNothing() {
        RevenueReport.Revenue found = revenue();
        assertThat(found.taken()).isEqualTo(new Dollars(0));
        assertThat(found).hasToString("0 permits, $0.00");
    }
}
