package io.github.rkaushikdev.jeepdolly.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Conflict;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import io.github.rkaushikdev.jeepdolly.domain.CarrierRegister;
import io.github.rkaushikdev.jeepdolly.domain.CommodityBook;
import io.github.rkaushikdev.jeepdolly.permits.Series;
import io.github.rkaushikdev.jeepdolly.persistence.models.CarrierRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.CommodityRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.CounterRow;
import io.github.rkaushikdev.jeepdolly.support.Applications;
import io.github.rkaushikdev.jeepdolly.support.Carriers;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The unit of work, and the counter that runs inside its transaction. */
class UnitOfWorkTest {

    private static final CommodityBook BOOK = CommodityBook.standard();
    private static final LocalDate FILED = LocalDate.of(2025, 4, 7);

    private Database factory;

    @BeforeEach
    void fileTheCommoditiesAndCarriers() {
        factory = Databases.empty();
        try (UnitOfWork work = factory.unitOfWork()) {
            for (String code : BOOK.codes()) {
                work.add(CommodityRow.of(BOOK.get(code)));
            }
            CarrierRegister register = Carriers.aCarrierRegister();
            for (int usdot : List.of(Carriers.BICKFORD, Carriers.AROOSTOOK)) {
                work.add(CarrierRow.of(register.get(usdot)));
            }
            work.commit();
        }
    }

    private void assertNothingFiledUnder(String reference) {
        try (UnitOfWork work = factory.unitOfWork(BOOK)) {
            assertThatThrownBy(() -> work.applications().get(reference)).isInstanceOf(NotOnFile.class);
        }
    }

    @Test
    void workThatIsCommittedIsThereAfterwards() {
        try (UnitOfWork work = factory.unitOfWork(BOOK)) {
            work.applications().add(Applications.anApplication());
            work.commit();
        }
        try (UnitOfWork work = factory.unitOfWork(BOOK)) {
            assertThat(work.applications().get("25-04471").origin()).isEqualTo("waterville");
        }
    }

    @Test
    void workThatIsNotCommittedNeverHappened() {
        try (UnitOfWork work = factory.unitOfWork(BOOK)) {
            work.applications().add(Applications.anApplication());
        }
        assertNothingFiledUnder("25-04471");
    }

    @Test
    void anExceptionOnTheWayOutRollsBackWithoutBeingAsked() {
        assertThatThrownBy(() -> {
                    try (UnitOfWork work = factory.unitOfWork(BOOK)) {
                        work.applications().add(Applications.anApplication());
                        work.flush();
                        throw new IllegalStateException("something went wrong halfway through");
                    }
                })
                .isInstanceOf(IllegalStateException.class);
        assertNothingFiledUnder("25-04471");
    }

    @Test
    void aRollbackLeavesTheWorkOpenForMore() {
        try (UnitOfWork work = factory.unitOfWork(BOOK)) {
            work.applications().add(Applications.anApplication("25-00001"));
            work.rollback();
            work.applications().add(Applications.anApplication("25-00002"));
            work.commit();
        }
        try (UnitOfWork work = factory.unitOfWork(BOOK)) {
            assertThat(work.applications().get("25-00002")).isNotNull();
        }
        assertNothingFiledUnder("25-00001");
    }

    @Test
    void aUnitOfWorkThatIsNotOpenRefusesToHandOutAnything() {
        UnitOfWork work = factory.unitOfWork(BOOK);
        work.close();
        assertThat(work.isOpen()).isFalse();
        assertThatThrownBy(work::applications).isInstanceOf(Conflict.class);
    }

    @Test
    void theRepositoriesAllComeOffTheSameSession() {
        try (UnitOfWork work = factory.unitOfWork(BOOK)) {
            assertThat(work.carriers().entityManager()).isSameAs(work.entityManager());
            assertThat(work.permits().entityManager()).isSameAs(work.entityManager());
            assertThat(work.network().entityManager()).isSameAs(work.entityManager());
            assertThat(work.commodities().entityManager()).isSameAs(work.entityManager());
        }
    }

    @Test
    void theCommodityBookIsReadFromTheDatabaseWhenNoneWasGiven() {
        try (UnitOfWork work = factory.unitOfWork()) {
            assertThat(work.applications().commodities().size()).isEqualTo(BOOK.size());
        }
    }

    @Test
    void theCounterStartsAtOneAndClimbs() {
        try (UnitOfWork work = factory.unitOfWork(BOOK)) {
            assertThat(work.counter().take(2025, Series.SINGLE)).isEqualTo(1);
            assertThat(work.counter().take(2025, Series.SINGLE)).isEqualTo(2);
            assertThat(work.counter().peek(2025, Series.SINGLE)).isEqualTo(3);
            assertThat(work.counter().issued(2025, Series.SINGLE)).isEqualTo(2);
            work.commit();
        }
    }

    @Test
    void eachSeriesCountsOnItsOwn() {
        try (UnitOfWork work = factory.unitOfWork(BOOK)) {
            work.counter().take(2025, Series.SINGLE);
            assertThat(work.counter().take(2025, Series.SUPERLOAD)).isEqualTo(1);
            assertThat(work.counter().take(2026, Series.SINGLE)).isEqualTo(1);
            work.commit();
        }
    }

    @Test
    void aNumberTakenAndNotCommittedIsHandedOutAgain() {
        try (UnitOfWork work = factory.unitOfWork(BOOK)) {
            assertThat(work.counter().take(2025, Series.SINGLE)).isEqualTo(1);
        }
        try (UnitOfWork work = factory.unitOfWork(BOOK)) {
            assertThat(work.counter().take(2025, Series.SINGLE)).isEqualTo(1);
            work.commit();
        }
        try (UnitOfWork work = factory.unitOfWork(BOOK)) {
            assertThat(work.counter().take(2025, Series.SINGLE)).isEqualTo(2);
            work.commit();
        }
    }

    @Test
    void theCounterBuildsAWholePermitNumberForADay() {
        try (UnitOfWork work = factory.unitOfWork(BOOK)) {
            assertThat(work.counter().number(FILED)).hasToString("25-P-000001");
            assertThat(work.counter().number(FILED, Series.SUPERLOAD)).hasToString("25-S-000001");
            work.commit();
        }
    }

    @Test
    void aFullSeriesRefusesRatherThanWrapping() {
        try (UnitOfWork work = factory.unitOfWork(BOOK)) {
            work.counter().take(2025, Series.SINGLE);
            CounterRow row = work.entityManager().find(CounterRow.class, new CounterRow.Key(2025, "P"));
            row.setNextSequence(1000000);
            work.flush();
            assertThatThrownBy(() -> work.counter().take(2025, Series.SINGLE)).isInstanceOf(Conflict.class);
        }
    }
}
