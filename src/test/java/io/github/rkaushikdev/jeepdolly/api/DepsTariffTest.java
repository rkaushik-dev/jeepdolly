package io.github.rkaushikdev.jeepdolly.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.Logs;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.domain.HighwayClass;
import io.github.rkaushikdev.jeepdolly.limits.PermitKind;
import io.github.rkaushikdev.jeepdolly.limits.SizeBook;
import io.github.rkaushikdev.jeepdolly.limits.StatutoryTable;
import io.github.rkaushikdev.jeepdolly.permits.Office;
import io.github.rkaushikdev.jeepdolly.persistence.Reference;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.persistence.models.BaseFeeRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.StatutoryRow;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import jakarta.persistence.EntityManager;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

/** Where the office gets its tables from, now that they are on rows. */
class DepsTariffTest {

    private static final LocalDate MARCH = LocalDate.of(2025, 3, 3);
    private static final LocalDate APRIL = LocalDate.of(2025, 4, 8);
    private static final LocalDate FIRST = LocalDate.of(2024, 1, 1);

    private final ByteArrayOutputStream stream = new ByteArrayOutputStream();

    @AfterEach
    void pointTheLoggerBackAtStandardError() {
        Logs.configure(Level.WARN, null);
    }

    private String logged() {
        return stream.toString(StandardCharsets.UTF_8);
    }

    @Test
    void theOfficeReadsTheStatutoryTablesOffTheRows() {
        Pounds before;
        Pounds after;
        try (UnitOfWork work = Databases.seeded().unitOfWork()) {
            EntityManager db = work.entityManager();
            StatutoryTable table = Reference.statutesFor(db).on(HighwayClass.ARTERIAL, MARCH);
            before = table.gross();
            StatutoryRow row = db.find(StatutoryRow.class, new StatutoryRow.Key("arterial", FIRST));
            row.setGross(new Pounds(104000));
            work.flush();
            after = Reference.statutesFor(db).on(HighwayClass.ARTERIAL, MARCH).gross();
        }
        assertThat(before).isEqualTo(new Pounds(100000));
        assertThat(after).isEqualTo(new Pounds(104000));
    }

    @Test
    void theOfficeReadsTheSizeTablesOffTheRows() {
        SizeBook book;
        try (UnitOfWork work = Databases.seeded().unitOfWork()) {
            book = Reference.sizesFor(work.entityManager(), MARCH);
        }
        assertThat(book.size()).isEqualTo(4);
        assertThat(book.on(HighwayClass.STATE_AID).routine().width().value()).isEqualTo(168);
    }

    @Test
    void theOfficeReadsTheFeeSchedulesOffTheRows() {
        try (UnitOfWork work = Databases.seeded().unitOfWork()) {
            assertThat(Reference.schedulesFor(work.entityManager()).on(MARCH).baseFor(PermitKind.ORDINARY))
                    .isEqualTo(Dollars.of(40));
        }
    }

    @Test
    void aBareDatabaseFallsBackToTheCodedTablesAndSaysSo() {
        Logs.configure(Level.INFO, stream);
        StatutoryTable table;
        try (UnitOfWork work = Databases.empty().unitOfWork()) {
            table = Reference.statutesFor(work.entityManager()).on(HighwayClass.ARTERIAL, MARCH);
        }
        assertThat(table.gross()).isEqualTo(new Pounds(100000));
        assertThat(logged()).contains("tariff.unseeded table=statutory_limits");
    }

    @Test
    void theFallbackCoversAllThreeBooks() {
        Logs.configure(Level.INFO, stream);
        try (UnitOfWork work = Databases.empty().unitOfWork()) {
            assertThat(Reference.sizesFor(work.entityManager(), MARCH).size()).isEqualTo(4);
            assertThat(Reference.schedulesFor(work.entityManager()).size()).isEqualTo(1);
        }
        assertThat(logged()).contains("table=size_limits");
        assertThat(logged()).contains("table=fee_schedules");
    }

    @Test
    void theWholeOfficeComesUpOnASeededDatabase() {
        Office desk;
        try (UnitOfWork work = Databases.seeded().unitOfWork()) {
            desk = Deps.officeFrom(work, APRIL);
        }
        assertThat(desk.statutes().size()).isEqualTo(4);
        assertThat(desk.sizes().size()).isEqualTo(4);
        assertThat(desk.schedules().size()).isEqualTo(1);
    }

    @Test
    void aChangeToARowChangesWhatTheOfficePricesWith() {
        try (UnitOfWork work = Databases.seeded().unitOfWork()) {
            EntityManager db = work.entityManager();
            db.find(BaseFeeRow.class, new BaseFeeRow.Key(FIRST, "ordinary")).setAmount(Dollars.of(55));
            work.flush();
            assertThat(Reference.schedulesFor(db).on(MARCH).baseFor(PermitKind.ORDINARY))
                    .isEqualTo(Dollars.of(55));
        }
    }
}
