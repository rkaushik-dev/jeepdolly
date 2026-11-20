package io.github.rkaushikdev.jeepdolly.support;

import io.github.rkaushikdev.jeepdolly.domain.Carrier;
import io.github.rkaushikdev.jeepdolly.domain.CarrierRegister;
import io.github.rkaushikdev.jeepdolly.domain.CommodityBook;
import io.github.rkaushikdev.jeepdolly.domain.Network;
import io.github.rkaushikdev.jeepdolly.domain.Node;
import io.github.rkaushikdev.jeepdolly.domain.Segment;
import io.github.rkaushikdev.jeepdolly.escorts.Company;
import io.github.rkaushikdev.jeepdolly.escorts.EscortRegister;
import io.github.rkaushikdev.jeepdolly.escorts.Operator;
import io.github.rkaushikdev.jeepdolly.persistence.Database;
import io.github.rkaushikdev.jeepdolly.persistence.TariffStore;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.persistence.models.BondRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.CarrierRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.CommodityRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.InsuranceRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.NodeRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.PilotCardRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.PilotCompanyRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.SegmentRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.StructureRow;
import io.github.rkaushikdev.jeepdolly.structures.Structure;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * The suite's database: one H2 in memory, migrated once for the whole run and emptied before each
 * test that asks for it.
 *
 * <p>The original built and threw away an in memory SQLite database per test. Building a Hibernate
 * factory per test costs more than the tests do, so this keeps one database and one factory and
 * gives every test what the original did: the schema, and either nothing in it or the reference
 * rows every office is built from, with the serial keys counting from one.
 */
public final class Databases {

    /** The one database the suite shares. */
    public static final String URL = "jdbc:h2:mem:jeepdolly-tests;DB_CLOSE_DELAY=-1";

    private static Database shared;

    private Databases() {}

    /** The schema and nothing in it, for a test that wants to fill it itself. */
    public static synchronized Database empty() {
        if (shared == null) {
            shared = Database.open(URL);
            shared.migrate();
        }
        truncate(shared);
        return shared;
    }

    /** The schema with the reference tables and the tariff filled, which an office is built from. */
    public static Database seeded() {
        Database database = empty();
        try (UnitOfWork work = database.unitOfWork()) {
            seed(work);
            TariffStore.seed(work.entityManager());
            work.commit();
        }
        return database;
    }

    /** Everything the reference tables hold, so an office can be built: support.py's seed. */
    public static void seed(UnitOfWork work) {
        CommodityBook book = CommodityBook.standard();
        for (String code : book.codes()) {
            work.add(CommodityRow.of(book.get(code)));
        }
        CarrierRegister register = Carriers.aCarrierRegister();
        for (int usdot : List.of(Carriers.BICKFORD, Carriers.AROOSTOOK)) {
            Carrier carrier = register.get(usdot);
            work.add(CarrierRow.of(carrier));
            carrier.insurance().ifPresent(cover -> work.add(InsuranceRow.of(usdot, cover)));
            carrier.bond().ifPresent(bond -> work.add(BondRow.of(usdot, bond)));
        }
        Network network = RoadLog.roadLog();
        for (Node node : network.nodes()) {
            work.add(NodeRow.of(node));
        }
        for (Segment segment : network.segments()) {
            work.add(SegmentRow.of(segment));
        }
        work.commit();
        List<String> idents = network.segments().stream().map(Segment::ident).toList();
        for (Structure structure : Structures.structureBook().onSegments(idents)) {
            work.add(StructureRow.of(structure));
        }
        work.commit();
        EscortRegister escorts = Pilots.anEscortRegister();
        for (Company company : escorts.companies()) {
            work.add(PilotCompanyRow.of(company));
        }
        work.commit();
        for (Operator operator : escorts.operators()) {
            work.add(PilotCardRow.of(operator));
        }
        work.commit();
    }

    private static void truncate(Database database) {
        List<String> tables = database.tableNames();
        try (Connection connection = database.dataSource().getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("SET REFERENTIAL_INTEGRITY FALSE");
            for (String table : tables) {
                statement.execute("TRUNCATE TABLE " + table + " RESTART IDENTITY");
            }
            statement.execute("SET REFERENTIAL_INTEGRITY TRUE");
        } catch (SQLException trouble) {
            throw new IllegalStateException("could not empty the test database", trouble);
        }
    }
}
