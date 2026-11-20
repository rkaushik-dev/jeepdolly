package io.github.rkaushikdev.jeepdolly.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.Miles;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.domain.HighwayClass;
import io.github.rkaushikdev.jeepdolly.samples.SampleRow;
import io.github.rkaushikdev.jeepdolly.samples.SampleTables;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

/** The database floor: the column types, the naming, and the in memory database. */
class PersistenceBaseTest {

    private final List<AutoCloseable> opened = new ArrayList<>();
    private UnitOfWork session;

    @BeforeEach
    void openASession() {
        session = sampleSession(Database.inMemory());
    }

    @AfterEach
    void closeEverything() throws Exception {
        for (AutoCloseable one : opened.reversed()) {
            one.close();
        }
    }

    private UnitOfWork sampleSession(Database database) {
        opened.add(database);
        LocalContainerEntityManagerFactoryBean bean = SampleTables.over(database.dataSource());
        opened.add(bean::destroy);
        UnitOfWork work = new UnitOfWork(SampleTables.factory(bean).createEntityManager());
        opened.add(work);
        return work;
    }

    private static SampleRow aRow(String ident) {
        return new SampleRow(
                ident,
                new Pounds(132400),
                Inches.of(14, 6),
                Miles.of(35, 20),
                Dollars.of(188, 72),
                HighwayClass.STATE_AID,
                LocalDate.of(2025, 4, 8));
    }

    private static List<SampleRow> everything(UnitOfWork work) {
        return work.entityManager()
                .createQuery("select s from SampleRow s", SampleRow.class)
                .getResultList();
    }

    @Test
    void everyMeasureComesBackAsTheTypeItWentInAs() {
        session.add(aRow("one"));
        session.commit();
        session.entityManager().clear();
        SampleRow found = everything(session).get(0);
        assertThat(found.weight()).isEqualTo(new Pounds(132400));
        assertThat(found.size()).isEqualTo(new Inches(174));
        assertThat(found.distance()).isEqualTo(new Miles(3520));
        assertThat(found.fee()).isEqualTo(new Dollars(18872));
        assertThat(found.road()).isSameAs(HighwayClass.STATE_AID);
        assertThat(found.day()).isEqualTo(LocalDate.of(2025, 4, 8));
    }

    @Test
    void theMeasuresAreStoredAsWholeNumbersAndNotAsText() {
        session.add(aRow("one"));
        session.commit();
        Object[] row = (Object[]) session.entityManager()
                .createNativeQuery("select weight, size, distance, fee, road from sample_measures")
                .getSingleResult();
        assertThat(row[0]).isEqualTo(132400);
        assertThat(row[1]).isEqualTo(174);
        assertThat(row[2]).isEqualTo(3520);
        assertThat(row[3]).isEqualTo(18872L);
        assertThat(row[4]).isEqualTo("state aid");
    }

    @Test
    void aNullMeasureComesBackAsNoneRatherThanAZero() {
        session.add(aRow("two"));
        session.commit();
        session.entityManager().clear();
        SampleRow found = session.entityManager().find(SampleRow.class, "two");
        assertThat(found.spare()).isNull();
        assertThat(found.spareRoad()).isNull();
        assertThat(found.weight()).isEqualTo(new Pounds(132400));
    }

    @Test
    void thePrimaryKeyIsNamedAfterTheConvention() throws SQLException {
        // The original read the name off the metadata's naming convention. Here the convention
        // lives in the migrations, so every table they build is checked for a key named after it.
        Database database = Database.inMemory();
        opened.add(database);
        database.migrate();
        int walked = 0;
        try (Connection connection = database.dataSource().getConnection()) {
            for (String table : database.tableNames()) {
                try (ResultSet keys =
                        connection.getMetaData().getPrimaryKeys(null, null, table.toUpperCase(Locale.ROOT))) {
                    assertThat(keys.next()).as(table).isTrue();
                    assertThat(keys.getString("PK_NAME").toLowerCase(Locale.ROOT))
                            .isEqualTo("pk_" + table);
                }
                walked++;
            }
        }
        assertThat(walked).isGreaterThan(15);
    }

    @Test
    void theMemoryEngineKeepsOneDatabaseAcrossConnections() {
        Database database = Database.inMemory();
        LocalContainerEntityManagerFactoryBean bean = SampleTables.over(database.dataSource());
        opened.add(database);
        opened.add(bean::destroy);
        try (UnitOfWork first = new UnitOfWork(SampleTables.factory(bean).createEntityManager())) {
            first.add(aRow("one"));
            first.commit();
        }
        try (UnitOfWork second = new UnitOfWork(SampleTables.factory(bean).createEntityManager())) {
            assertThat(everything(second)).extracting(SampleRow::ident).containsExactly("one");
        }
    }

    @Test
    void twoMemoryEnginesDoNotShareAnything() {
        UnitOfWork first = sampleSession(Database.inMemory());
        first.add(aRow("one"));
        first.commit();
        UnitOfWork second = sampleSession(Database.inMemory());
        assertThat(everything(second)).isEmpty();
    }

    @Test
    void theSameKeyTwiceIsRefusedByTheDatabase() {
        session.add(aRow("one"));
        session.commit();
        assertThatThrownBy(() -> {
                    session.add(aRow("one"));
                    session.commit();
                })
                .isInstanceOf(IntegrityViolation.class);
    }

    @Test
    void aDateIsReadFromAStringWhenItArrivesAsOne() {
        assertThat(Columns.asDate("2025-04-08")).isEqualTo(LocalDate.of(2025, 4, 8));
        assertThat(Columns.asDate(LocalDate.of(2025, 4, 8))).isEqualTo(LocalDate.of(2025, 4, 8));
    }
}
