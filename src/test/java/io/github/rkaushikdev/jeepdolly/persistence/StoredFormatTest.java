package io.github.rkaushikdev.jeepdolly.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.Miles;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.SeasonBook;
import io.github.rkaushikdev.jeepdolly.domain.Billing;
import io.github.rkaushikdev.jeepdolly.domain.Carrier;
import io.github.rkaushikdev.jeepdolly.domain.Commodity;
import io.github.rkaushikdev.jeepdolly.domain.HighwayClass;
import io.github.rkaushikdev.jeepdolly.domain.Network;
import io.github.rkaushikdev.jeepdolly.domain.Restriction;
import io.github.rkaushikdev.jeepdolly.domain.Segment;
import io.github.rkaushikdev.jeepdolly.enforcement.EnforcementBook;
import io.github.rkaushikdev.jeepdolly.escorts.Endorsement;
import io.github.rkaushikdev.jeepdolly.escorts.Operator;
import io.github.rkaushikdev.jeepdolly.fees.FeeSchedule;
import io.github.rkaushikdev.jeepdolly.limits.PermitKind;
import io.github.rkaushikdev.jeepdolly.persistence.models.AuditRow;
import io.github.rkaushikdev.jeepdolly.structures.Structure;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import io.github.rkaushikdev.jeepdolly.utilities.Crossing;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Rows stored in the schema's own encodings read back as the same office.
 *
 * <p>The schema stores enumerations as their short values, every measure as a whole integer, money
 * as cents, booleans as {@code 1} and {@code 0}, dates as ISO text, times and timestamps with six
 * places of fraction, and a curfew's days as weekday digits with Monday as {@code 0}. An existing
 * database holds its rows in exactly that shape. Each row here is typed in by hand in that shape,
 * below the entities, and read back up through the same books and stores the office decides with.
 * The last two tests are the controls: a row in a shape the schema never holds is refused rather
 * than read as something else.
 */
class StoredFormatTest {

    private static final LocalDate APRIL = LocalDate.of(2025, 4, 8);

    private Database database;

    @BeforeEach
    void writeRowsTheWayTheSchemaStoresThem() throws SQLException {
        database = Databases.empty();
        sql(
                "insert into carriers (usdot, name, town, state, billing)"
                        + " values (1204471, 'Bickford Heavy Haul', 'Newport', 'ME', 'account')",
                "insert into insurance_certificates (policy, usdot, insurer, liability, effective, expires, filed)"
                        + " values ('CA-88214', 1204471, 'Acadia Mutual', 100000000, '2025-01-01', '2025-12-31', null)",
                "insert into bonds (number, usdot, surety, amount, effective, expires)"
                        + " values ('SB-4471', 1204471, 'Penobscot Surety', 2500000, '2025-01-01', '2025-12-31')",
                "insert into suspensions (id, usdot, reason, from_day, until, ordered_by)"
                        + " values (1, 1204471, 'late affidavit', '2025-02-01', '2025-02-28', 'supervisor')",
                "insert into commodities (code, description, family, divisible, allowance, tip_limit_degrees,"
                        + " high_centre, retired, changed_by, changed_on)"
                        + " values ('WOOD', 'round wood', 'forest', 1, 'forest products', null, 0, null, '', null),"
                        + " ('XFMR', 'power transformer', 'utility', 0, null, 3, 0, null, 'clerk', '2025-03-03')",
                "insert into nodes (name, town) values ('skowhegan', 'Skowhegan'), ('norridgewock', 'Norridgewock')",
                "insert into segments (ident, route_kind, route_number, route_suffix, start_node, end_node,"
                        + " length_hundredths, county, town, district, one_way)"
                        + " values ('US2-1', 'US', 2, '', 'skowhegan', 'norridgewock', 620, 'Somerset', 'Skowhegan', 4, 0)",
                "insert into restrictions (ident, segment_ident, kind, note, height, width, weight, region,"
                        + " curfew_days, curfew_from, curfew_to, effective, expires)"
                        + " values ('R-1', 'US2-1', 'clearance', 'railroad bridge', 165, null, null, null, null, null,"
                        + " null, null, null),"
                        + " ('R-2', 'US2-1', 'curfew', 'rush hour', null, null, null, null, '01234',"
                        + " '06:30:00.000000', '08:30:00.000000', '2025-01-01', null),"
                        + " ('R-3', 'US2-1', 'seasonal', 'spring posting', null, null, null, 'district 4', null, null,"
                        + " null, null, null)",
                "insert into structures (ident, name, segment_ident, kind, spans, built, inspected,"
                        + " inventory_rating, operating_rating, posted, clearance, deck_width)"
                        + " values ('1247', 'Kennebec River', 'US2-1', 'bridge', '120,120,120', 1937, '2024-06-01',"
                        + " 88000, 132000, null, null, null),"
                        + " ('4102', 'Pittsfield gantry', 'US2-1', 'sign gantry', '', 1998, null, null, null, null, 198,"
                        + " null)",
                "insert into season_orders (number, region, kind, starts, ends, share, signed_by, note)"
                        + " values ('25-04', 'district 4', 'thaw', '2025-03-17', '2025-05-02', 65, 'R Pelletier', '')",
                "insert into pilot_companies (ident, name, town, telephone, suspended, note)"
                        + " values ('PC-1', 'Kennebec Pilot Car', 'Waterville', '207 555 0142', 0, '')",
                "insert into pilot_cards (card, name, company_ident, issued, expires, endorsements, suspended)"
                        + " values ('C-1002', 'M Levesque', 'PC-1', '2023-05-01', '2026-04-30',"
                        + " 'height pole,police liaison', 0)",
                "insert into citations (number, written, filed, officer, usdot, carrier_name, offence, place,"
                        + " permit, over, base, multiplier, amount, schedule, state, closing_reason, note)"
                        + " values ('E-25-0417', '2025-04-08', '2025-04-08', 'Sgt A Thibodeau', 1204471,"
                        + " 'Bickford Heavy Haul', 'overweight', 'US201-2 at Fairfield', null, 12000, 58000, 1, 58000,"
                        + " '2024-01-01', 'contested', '', '')",
                "insert into line_owners (ident, name, telephone, notice_days, note)"
                        + " values ('CMP', 'Central Maine Power', '207 555 0100', 5, '')",
                "insert into line_crossings (ident, segment_ident, place, height, owner_ident, lift_to, measured, note)"
                        + " values ('L-1', 'US2-1', 'Norridgewock village', 228, 'CMP', 312, '2024-09-12', '')",
                "insert into fee_schedules (effective, review, amendment, minimum, annual)"
                        + " values ('2024-01-01', 20000, 2500, 4000, 50000)",
                "insert into fee_base (effective, kind, amount) values ('2024-01-01', 'ordinary', 4000),"
                        + " ('2024-01-01', 'study', 12500), ('2024-01-01', 'superload', 35000)",
                "insert into fee_per_mile (effective, highway_class, amount) values ('2024-01-01', 'interstate', 12),"
                        + " ('2024-01-01', 'arterial', 18), ('2024-01-01', 'state aid', 26), ('2024-01-01', 'town way', 0)",
                "insert into fee_weight_steps (effective, position, up_to, per_block, note)"
                        + " values ('2024-01-01', 0, 20000, 500, ''), ('2024-01-01', 1, 60000, 800, ''),"
                        + " ('2024-01-01', 2, null, 1200, '')",
                "insert into audit_trail (id, happened, action, actor, kind, reference, detail)"
                        + " values (1, '2025-04-08 10:15:00.000000', 'permit.issued', 'Dana', 'permit', '25-P-004471', '')");
    }

    private void sql(String... statements) throws SQLException {
        try (Connection connection = database.dataSource().getConnection();
                Statement statement = connection.createStatement()) {
            for (String one : statements) {
                statement.execute(one);
            }
        }
    }

    @Test
    void aCarrierAndItsPaperReadBackAsTheyWereFiled() {
        try (UnitOfWork work = database.unitOfWork()) {
            Carrier carrier = Books.carriersFrom(work.entityManager(), APRIL).get(1204471);
            assertThat(carrier.billing()).isSameAs(Billing.ACCOUNT);
            assertThat(carrier.insurance().orElseThrow().liability()).isEqualTo(Dollars.of(1000000));
            assertThat(carrier.bond().orElseThrow().amount()).isEqualTo(Dollars.of(25000));
            assertThat(carrier.suspensions()).hasSize(1);
            assertThat(carrier.suspensionOn(LocalDate.of(2025, 2, 15))).isPresent();
            assertThat(carrier.inGoodStanding(APRIL, APRIL)).isTrue();
        }
    }

    @Test
    void theRoadLogItsRestrictionsAndItsStructuresReadBack() {
        try (UnitOfWork work = database.unitOfWork()) {
            Network network = Books.networkFrom(work.entityManager());
            Segment segment = network.segment("US2-1");
            assertThat(segment.length()).isEqualTo(new Miles(620));
            assertThat(segment.highwayClass()).isSameAs(HighwayClass.ARTERIAL);
            assertThat(segment.oneWay()).isFalse();
            List<Restriction> restrictions =
                    Books.restrictionsFrom(work.entityManager()).onSegment("US2-1", APRIL);
            assertThat(restrictions).extracting(Restriction::ident).containsExactly("R-1", "R-2", "R-3");
            assertThat(restrictions.get(0).height()).contains(new Inches(165));
            assertThat(restrictions.get(1).curfew().orElseThrow().covers(LocalDate.of(2025, 4, 7), LocalTime.of(7, 0)))
                    .isTrue();
            assertThat(restrictions.get(1).curfew().orElseThrow().covers(LocalDate.of(2025, 4, 12), LocalTime.of(7, 0)))
                    .isFalse();
            assertThat(restrictions.get(2).region()).contains("district 4");
            Structure bridge = Books.structuresFrom(work.entityManager()).get("1247");
            assertThat(bridge.spansFeet()).containsExactly(120, 120, 120);
            assertThat(bridge.operatingRating()).contains(new Pounds(132000));
            assertThat(Books.structuresFrom(work.entityManager()).get("4102").clearance())
                    .contains(new Inches(198));
        }
    }

    @Test
    void theCommodityTableAndTheSeasonalOrdersReadBack() {
        try (UnitOfWork work = database.unitOfWork()) {
            Commodity wood = work.commodities().book().get("WOOD");
            assertThat(wood.divisible()).isTrue();
            assertThat(wood.allowance()).contains("forest products");
            assertThat(work.commodities().book().get("XFMR").tipLimitDegrees()).contains(3);
            SeasonBook seasons = SeasonStore.bookFrom(work.entityManager());
            assertThat(seasons.limitOn("district 4", APRIL, new Pounds(80000))).isEqualTo(new Pounds(52000));
        }
    }

    @Test
    void theRegistersAndTheAccountsReadBack() {
        try (UnitOfWork work = database.unitOfWork()) {
            Operator card = Books.escortsFrom(work.entityManager()).operator("C-1002");
            assertThat(card.endorsements()).isEqualTo(Set.of(Endorsement.HEIGHT_POLE, Endorsement.POLICE_LIAISON));
            EnforcementBook enforcement = Books.enforcementFrom(work.entityManager());
            assertThat(enforcement.account(1204471).owing()).isEqualTo(Dollars.of(580));
            assertThat(enforcement.account(1204471).unpaid().get(0).contested()).isTrue();
            Crossing wire = Books.linesFrom(work.entityManager()).get("L-1");
            assertThat(wire.height()).isEqualTo(Inches.of(19));
            assertThat(wire.liftTo()).contains(Inches.of(26));
        }
    }

    @Test
    void theFeeTablesPriceTheWayTheCodedScheduleDoes() {
        try (UnitOfWork work = database.unitOfWork()) {
            FeeSchedule schedule =
                    TariffStore.scheduleBook(work.entityManager()).on(APRIL);
            FeeSchedule coded = FeeSchedule.standard();
            assertThat(schedule.base()).isEqualTo(coded.base());
            assertThat(schedule.perMile()).isEqualTo(coded.perMile());
            assertThat(schedule.steps()).isEqualTo(coded.steps());
            assertThat(schedule.baseFor(PermitKind.SUPERLOAD)).isEqualTo(Dollars.of(350));
            assertThat(schedule.weightCharge(new Pounds(100000))).isEqualTo(coded.weightCharge(new Pounds(100000)));
        }
    }

    @Test
    void aTimestampReadsBackAtTheClockTimeItWasWrittenAt() {
        try (UnitOfWork work = database.unitOfWork()) {
            AuditRow row = work.entityManager().find(AuditRow.class, 1);
            assertThat(row.getHappened()).isEqualTo(LocalDateTime.of(2025, 4, 8, 10, 15));
            assertThat(row.getAction()).isEqualTo("permit.issued");
        }
    }

    @Test
    void anEnumerationStoredByItsConstantNameIsRefusedRatherThanGuessed() throws SQLException {
        sql("update restrictions set kind = 'CLEARANCE' where ident = 'R-1'");
        try (UnitOfWork work = database.unitOfWork()) {
            assertThatThrownBy(() -> Books.restrictionsFrom(work.entityManager()))
                    .hasRootCauseInstanceOf(IllegalArgumentException.class)
                    .hasStackTraceContaining("'CLEARANCE' is not a valid RestrictionKind");
        }
    }

    @Test
    void aCurfewDigitPastSundayIsRefusedRatherThanGuessed() throws SQLException {
        sql("update restrictions set curfew_days = '0129' where ident = 'R-2'");
        try (UnitOfWork work = database.unitOfWork()) {
            assertThatThrownBy(() -> Books.restrictionsFrom(work.entityManager()))
                    .isInstanceOf(Impossible.class)
                    .hasMessageContaining("days of the week");
        }
    }
}
