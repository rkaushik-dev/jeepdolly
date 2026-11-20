package io.github.rkaushikdev.jeepdolly.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Conflict;
import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.domain.HighwayClass;
import io.github.rkaushikdev.jeepdolly.fees.FeeSchedule;
import io.github.rkaushikdev.jeepdolly.limits.PermitKind;
import io.github.rkaushikdev.jeepdolly.limits.SizeTable;
import io.github.rkaushikdev.jeepdolly.limits.StatutoryTable;
import io.github.rkaushikdev.jeepdolly.persistence.models.StatutoryRow;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** The published tables on rows, and the seed that puts today's numbers in. */
class TariffStoreTest {

    private static final LocalDate MARCH = LocalDate.of(2025, 3, 3);

    /** A database with the schema and nothing filed on it. */
    private static UnitOfWork bare() {
        return Databases.empty().unitOfWork();
    }

    /** The database the rest of the suite runs against, tariff and all. */
    private static UnitOfWork filled() {
        return Databases.seeded().unitOfWork();
    }

    @Test
    void aFreshDatabaseHasNoTablesOnIt() {
        try (UnitOfWork work = bare()) {
            assertThat(TariffStore.seeded(work.entityManager())).isFalse();
        }
    }

    @Test
    void seedingWritesATableForEveryClassOfRoad() {
        try (UnitOfWork work = filled()) {
            assertThat(TariffStore.seeded(work.entityManager())).isTrue();
            assertThat(work.entityManager()
                            .createQuery("select s from StatutoryRow s", StatutoryRow.class)
                            .getResultList())
                    .hasSize(4);
        }
    }

    @Test
    void seedingTwiceIsRefused() {
        try (UnitOfWork work = filled()) {
            assertThatThrownBy(() -> TariffStore.seed(work.entityManager())).isInstanceOf(Conflict.class);
        }
    }

    @Test
    void theStatutoryBookReadsBackTheNumbersThatWentIn() {
        StatutoryTable table;
        try (UnitOfWork work = filled()) {
            table = TariffStore.statutoryBook(work.entityManager()).on(HighwayClass.ARTERIAL, MARCH);
        }
        assertThat(table.groupLimit(1)).isEqualTo(new Pounds(22400));
        assertThat(table.groupLimit(2)).isEqualTo(new Pounds(41000));
        assertThat(table.groupLimit(5)).isEqualTo(new Pounds(63000));
        assertThat(table.gross()).isEqualTo(new Pounds(100000));
        assertThat(table.tireRate()).isEqualTo(600);
    }

    @Test
    void theInterstateTableIsStillTheFederalOne() {
        StatutoryTable table;
        try (UnitOfWork work = filled()) {
            table = TariffStore.statutoryBook(work.entityManager()).on(HighwayClass.INTERSTATE, MARCH);
        }
        assertThat(table.gross()).isEqualTo(new Pounds(80000));
        assertThat(table.effective()).isEqualTo(TariffStore.FIRST);
    }

    @Test
    void theSizeBookReadsBackBothBoxes() {
        SizeTable table;
        try (UnitOfWork work = filled()) {
            table = TariffStore.sizeBook(work.entityManager(), MARCH).on(HighwayClass.ARTERIAL);
        }
        assertThat(table.legal().width()).isEqualTo(Inches.of(8, 6));
        assertThat(table.routine().width()).isEqualTo(Inches.of(16));
        assertThat(table.routine().height()).isEqualTo(Inches.of(15, 6));
    }

    @Test
    void theSizeBookLeavesOutWhatHasNotTakenEffect() {
        try (UnitOfWork work = filled()) {
            assertThat(TariffStore.sizeBook(work.entityManager(), LocalDate.of(2023, 1, 1))
                            .size())
                    .isZero();
            assertThat(TariffStore.sizeBook(work.entityManager(), MARCH).size()).isEqualTo(4);
        }
    }

    @Test
    void theScheduleBookReadsBackTheFeesAndTheSteps() {
        FeeSchedule schedule;
        try (UnitOfWork work = filled()) {
            schedule = TariffStore.scheduleBook(work.entityManager()).on(MARCH);
        }
        assertThat(schedule.baseFor(PermitKind.ORDINARY)).isEqualTo(Dollars.of(40));
        assertThat(schedule.mileRate(HighwayClass.STATE_AID)).isEqualTo(new Dollars(26));
        assertThat(schedule.annual()).isEqualTo(Dollars.of(500));
        assertThat(schedule.steps()).hasSize(3);
    }

    @Test
    void theWeightStepsComeBackInOrderWithTheLastOneOpen() {
        FeeSchedule schedule;
        try (UnitOfWork work = filled()) {
            schedule = TariffStore.scheduleBook(work.entityManager()).on(MARCH);
        }
        assertThat(schedule.steps().get(0).upTo()).contains(new Pounds(20000));
        assertThat(schedule.steps().get(schedule.steps().size() - 1).upTo()).isEmpty();
        assertThat(schedule.weightCharge(new Pounds(25000))).isEqualTo(Dollars.of(140));
    }

    @Test
    void theSeededTablesPriceAPermitTheSameAsTheCodedOnes() {
        FeeSchedule fromRows;
        try (UnitOfWork work = filled()) {
            fromRows = TariffStore.scheduleBook(work.entityManager()).on(MARCH);
        }
        FeeSchedule coded = FeeSchedule.standard();
        assertThat(fromRows.weightCharge(new Pounds(100000))).isEqualTo(coded.weightCharge(new Pounds(100000)));
        assertThat(fromRows.baseFor(PermitKind.SUPERLOAD)).isEqualTo(coded.baseFor(PermitKind.SUPERLOAD));
    }

    @Test
    void theSeedCanBeDatedFromADayTheCallerNames() {
        StatutoryTable table;
        try (UnitOfWork work = bare()) {
            TariffStore.seed(work.entityManager(), LocalDate.of(2025, 7, 1));
            work.commit();
            table = TariffStore.statutoryBook(work.entityManager()).on(HighwayClass.ARTERIAL, LocalDate.of(2025, 8, 1));
        }
        assertThat(table.effective()).isEqualTo(LocalDate.of(2025, 7, 1));
    }
}
