package io.github.rkaushikdev.jeepdolly.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Conflict;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.SeasonBook;
import io.github.rkaushikdev.jeepdolly.SeasonKind;
import io.github.rkaushikdev.jeepdolly.SeasonOrder;
import io.github.rkaushikdev.jeepdolly.persistence.models.SeasonOrderRow;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Seasonal orders on rows, and the overlap rule enforced once. */
class SeasonStoreTest {

    private static final LocalDate MARCH = LocalDate.of(2025, 3, 20);
    private static final LocalDate APRIL = LocalDate.of(2025, 4, 8);
    private static final LocalDate MAY = LocalDate.of(2025, 5, 2);

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

    private static SeasonOrder thaw() {
        return thaw("25-04", "district 4", MARCH, MAY);
    }

    private static SeasonOrder thaw(String number, String region, LocalDate start, LocalDate end) {
        return new SeasonOrder(number, region, SeasonKind.THAW, start, end, 65);
    }

    @Test
    void anOrderGoesOnFileAndComesBackTheSame() {
        SeasonStore.fileOrder(db, thaw(), "R Pelletier", "frost gauge at four feet");
        work.commit();
        SeasonOrderRow row = SeasonStore.rowFor(db, "25-04");
        assertThat(row.toDomain()).isEqualTo(thaw());
        assertThat(row.getSignedBy()).isEqualTo("R Pelletier");
        assertThat(row).hasToString("<SeasonOrderRow 25-04 district 4>");
    }

    @Test
    void theBookBuiltFromTheRowsReadsTheWayTheRouterWants() {
        SeasonStore.fileOrder(db, thaw());
        work.commit();
        SeasonBook book = SeasonStore.bookFrom(db);
        assertThat(book.posted("district 4", APRIL)).isTrue();
        assertThat(book.posted("district 4", LocalDate.of(2025, 6, 1))).isFalse();
        assertThat(book.limitOn("district 4", APRIL, new Pounds(94000))).isEqualTo(new Pounds(61100));
    }

    @Test
    void twoOverlappingOrdersInOneRegionAreRefusedBeforeAnythingIsWritten() {
        SeasonStore.fileOrder(db, thaw());
        work.commit();
        assertThatThrownBy(
                        () -> SeasonStore.fileOrder(db, thaw("25-05", "district 4", APRIL, LocalDate.of(2025, 5, 20))))
                .isInstanceOf(Conflict.class);
        work.rollback();
        assertThat(SeasonStore.forRegion(work.entityManager(), "district 4")).hasSize(1);
    }

    @Test
    void theSameSpanInAnotherRegionIsADifferentOrder() {
        SeasonStore.fileOrder(db, thaw());
        SeasonStore.fileOrder(db, thaw("25-06", "district 3", MARCH, MAY));
        work.commit();
        assertThat(SeasonStore.forRegion(db, "district 3")).hasSize(1);
        assertThat(SeasonStore.bookFrom(db).size()).isEqualTo(2);
    }

    @Test
    void anOrderNumberIsUsedOnce() {
        SeasonStore.fileOrder(db, thaw());
        work.commit();
        assertThatThrownBy(() -> SeasonStore.fileOrder(db, thaw("25-04", "district 3", MARCH, MAY)))
                .isInstanceOf(Conflict.class);
    }

    @Test
    void whatIsInForceIsAskedByRegionAndDay() {
        SeasonStore.fileOrder(db, thaw());
        work.commit();
        assertThat(SeasonStore.inForce(db, "district 4", APRIL).orElseThrow().getNumber())
                .isEqualTo("25-04");
        assertThat(SeasonStore.inForce(db, "district 4", LocalDate.of(2025, 6, 1)))
                .isEmpty();
        assertThat(SeasonStore.inForce(db, "district 3", APRIL)).isEmpty();
    }

    @Test
    void anOrderLiftedEarlyKeepsItsRowAndGetsTheDayItLifted() {
        SeasonStore.fileOrder(db, thaw());
        work.commit();
        SeasonStore.lift(db, "25-04", APRIL);
        work.commit();
        SeasonOrderRow row = SeasonStore.rowFor(db, "25-04");
        assertThat(row.getEnds()).isEqualTo(APRIL);
        assertThat(SeasonStore.inForce(db, "district 4", LocalDate.of(2025, 4, 20)))
                .isEmpty();
        assertThat(SeasonStore.forRegion(db, "district 4")).hasSize(1);
    }

    @Test
    void liftingAnOrderBeforeItStartedEndsItWhereItBegan() {
        SeasonStore.fileOrder(db, thaw());
        work.commit();
        SeasonStore.lift(db, "25-04", LocalDate.of(2025, 1, 1));
        work.commit();
        assertThat(SeasonStore.rowFor(db, "25-04").getEnds()).isEqualTo(MARCH);
    }

    @Test
    void anOrderNobodySignedSaysSo() {
        assertThatThrownBy(() -> SeasonStore.rowFor(db, "25-99")).isInstanceOf(NotOnFile.class);
    }

    @Test
    void aRowTheDomainWouldRefuseIsStillRefusedByTheDatabase() {
        work.add(new SeasonOrderRow("25-09", "district 4", SeasonKind.THAW, MAY, MARCH, 65));
        assertThatThrownBy(work::commit).isInstanceOf(IntegrityViolation.class);
    }
}
