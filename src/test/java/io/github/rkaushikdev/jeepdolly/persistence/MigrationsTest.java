package io.github.rkaushikdev.jeepdolly.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

/**
 * The migrations, checked against the mapping they are supposed to build.
 *
 * <p>This catches the mistake everybody makes: a table or a column added to an entity and not to a
 * migration. One database is built by running every migration, one by Hibernate exporting the
 * mapping, and the two are compared table by table. A third is migrated and then deliberately
 * spoiled, which proves each comparison can tell two schemas apart and is not passing by reading
 * nothing.
 */
class MigrationsTest {

    /** One thing a schema holds per table, read the same way on every side. */
    enum Aspect {
        COLUMNS((schema, table) -> schema.columns(table)),
        PRIMARY_KEY((schema, table) -> schema.primaryKey(table)),
        UNIQUE_KEYS((schema, table) -> schema.uniqueKeys(table)),
        FOREIGN_KEYS((schema, table) -> schema.foreignKeys(table)),
        INDEXES((schema, table) -> schema.indexes(table));

        private final BiFunction<Schema, String, Collection<?>> reading;

        Aspect(BiFunction<Schema, String, Collection<?>> reading) {
            this.reading = reading;
        }

        Collection<?> of(Schema schema, String table) {
            return reading.apply(schema, table);
        }

        int total(Schema schema) {
            return schema.tables().stream()
                    .mapToInt(table -> of(schema, table).size())
                    .sum();
        }
    }

    private static Database migratedDatabase;
    private static Database declaredDatabase;
    private static Database plantedDatabase;
    private static Schema migrated;
    private static Schema declared;
    private static Schema planted;

    @BeforeAll
    static void buildThreeSchemas() throws SQLException {
        migratedDatabase = Database.inMemory();
        migratedDatabase.migrate();
        migrated = Schema.of(migratedDatabase.dataSource());

        declaredDatabase = Database.inMemory();
        LocalContainerEntityManagerFactoryBean export =
                Database.entityManagerFactoryBean(declaredDatabase.dataSource(), false, "create");
        export.afterPropertiesSet();
        export.destroy();
        declared = Schema.of(declaredDatabase.dataSource());

        plantedDatabase = Database.inMemory();
        plantedDatabase.migrate();
        try (Connection connection = plantedDatabase.dataSource().getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("alter table nodes add column planted integer");
            statement.execute("alter table condition_texts drop constraint pk_condition_texts");
            statement.execute(
                    "alter table condition_texts add constraint pk_condition_texts primary key (effective, code)");
            statement.execute("alter table commodities add constraint uq_commodities_description unique (description)");
            statement.execute("alter table bonds drop constraint fk_bonds_usdot_carriers");
            statement.execute("create index ix_carriers_town on carriers (town)");
            statement.execute("alter table line_owners add check (notice_days < 100)");
        }
        planted = Schema.of(plantedDatabase.dataSource());
    }

    @AfterAll
    static void closeThem() {
        migratedDatabase.close();
        declaredDatabase.close();
        plantedDatabase.close();
    }

    @Test
    void theMigrationBuildsEveryTableTheModelsDeclare() {
        assertThat(migrated.tables()).isEqualTo(declared.tables());
        assertThat(migrated.tables()).contains("permits");
    }

    @Test
    void everyTableHasTheSameColumnsInBoth() {
        int walked = 0;
        for (String table : declared.tables()) {
            assertThat(migrated.columns(table)).as(table).isEqualTo(declared.columns(table));
            walked++;
        }
        assertThat(walked).isGreaterThan(15);
    }

    @Test
    void theIndexesTheReportsNeedAreInTheMigration() {
        assertThat(migrated.indexes("permits"))
                .extracting(Schema.Index::name)
                .contains("ix_permits_valid_from", "ix_permits_usdot");
    }

    @Test
    void theForeignKeysAreNamedByTheConvention() {
        assertThat(migrated.foreignKeys("permits"))
                .extracting(Schema.ForeignKey::name)
                .contains("fk_permits_usdot_carriers", "fk_permits_application_applications");
    }

    @Test
    void theMigrationReversesAllTheWayBackToNothing() {
        try (Database database = Database.inMemory()) {
            database.migrate();
            assertThat(database.tableNames()).contains("permits");
            database.drop();
            assertThat(database.tableNames()).isEmpty();
        }
    }

    @Test
    void theMetadataCarriesTheNamingConventionThatNamesThem() {
        for (String table : migrated.tables()) {
            assertThat(migrated.constraintNames()).as(table).contains("pk_" + table);
            for (Schema.ForeignKey key : migrated.foreignKeys(table)) {
                assertThat(key.name())
                        .isEqualTo("fk_" + table + "_" + String.join("_", key.columns()) + "_" + key.referenced());
            }
            for (Schema.Index index : migrated.indexes(table)) {
                assertThat(index.name()).startsWith("ix_" + table + "_");
            }
        }
        assertThat(migrated.constraintNames())
                .isNotEmpty()
                .allSatisfy(name -> assertThat(name).matches("(pk|fk|ck|uq)_.+"))
                .noneMatch(name -> name.startsWith("constraint_"));
        // The control: a check added without a name is caught under the name the database invents.
        assertThat(planted.constraintNames()).anyMatch(name -> name.startsWith("constraint_"));
    }

    @Test
    void everyTableHasTheSamePrimaryKeyInBoth() {
        for (String table : declared.tables()) {
            assertThat(migrated.primaryKey(table)).as(table).isEqualTo(declared.primaryKey(table));
        }
    }

    @Test
    void everyTableHasTheSameUniqueKeysInBoth() {
        for (String table : declared.tables()) {
            assertThat(migrated.uniqueKeys(table)).as(table).isEqualTo(declared.uniqueKeys(table));
        }
    }

    @Test
    void everyTableHasTheSameForeignKeysInBoth() {
        for (String table : declared.tables()) {
            assertThat(migrated.foreignKeys(table)).as(table).isEqualTo(declared.foreignKeys(table));
        }
    }

    @Test
    void everyTableHasTheSameNamedIndexesInBoth() {
        for (String table : declared.tables()) {
            assertThat(migrated.indexes(table)).as(table).isEqualTo(declared.indexes(table));
        }
    }

    @Test
    void everyComparisonReadsSomethingOnBothSides() {
        for (Aspect aspect : Aspect.values()) {
            assertThat(aspect.total(migrated)).as("%s migrated", aspect).isPositive();
            assertThat(aspect.total(declared)).as("%s declared", aspect).isPositive();
        }
    }

    @Test
    void aPlantedDifferenceIsCaughtByEveryComparison() {
        List<Object[]> spoiled = List.of(
                new Object[] {Aspect.COLUMNS, "nodes"},
                new Object[] {Aspect.PRIMARY_KEY, "condition_texts"},
                new Object[] {Aspect.UNIQUE_KEYS, "commodities"},
                new Object[] {Aspect.FOREIGN_KEYS, "bonds"},
                new Object[] {Aspect.INDEXES, "carriers"});
        for (Object[] one : spoiled) {
            Aspect aspect = (Aspect) one[0];
            String table = (String) one[1];
            assertThat(aspect.of(planted, table))
                    .as("%s of %s", aspect, table)
                    .isNotEqualTo(aspect.of(migrated, table));
        }
        for (Aspect aspect : Aspect.values()) {
            assertThat(aspect.of(planted, "segments"))
                    .as("%s of segments", aspect)
                    .isEqualTo(aspect.of(migrated, "segments"));
        }
    }
}
