package io.github.rkaushikdev.jeepdolly.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

/**
 * One database: a connection pool, the schema's migrations, and the entity manager factory units
 * of work are opened from. The counterpart of the original's engine and session factory.
 *
 * <p>The default is a file in the working directory rather than a server, so a mistyped setting
 * cannot point a developer at production. The in memory database is here rather than in the tests
 * because the command line wants it too, for a dry run: a database that behaves exactly like the
 * real one and vanishes afterwards.
 *
 * <p>The entity manager factory is built the first time it is asked for, and it validates the
 * mapping against the schema when it is, so a database nobody has migrated can still be migrated
 * and dropped. Every unit of work this database opens reports its flushes to the listeners
 * registered with {@link #listen}.
 */
public final class Database implements AutoCloseable {

    /** Where the database lives when nothing says otherwise. */
    public static final String DEFAULT_URL = "jdbc:h2:file:./jeepdolly";

    /** Where the entities and their column converters live. */
    public static final String MAPPED_PACKAGE = "io.github.rkaushikdev.jeepdolly.persistence";

    /** Where the migrations live, one per revision of the schema. */
    public static final String MIGRATIONS = "classpath:db/migration";

    /**
     * Column names the schema uses that H2 otherwise reads as keywords. The original ran on SQLite
     * and Postgres, where {@code year} is an ordinary name; the counter table has one.
     */
    static final String H2_NON_KEYWORDS = "NON_KEYWORDS=YEAR";

    private static final String HISTORY_TABLE = "flyway_schema_history";

    private final String url;
    private final boolean echoSql;
    private final HikariDataSource pool;
    private final List<FlushListener> listeners = new CopyOnWriteArrayList<>();
    private LocalContainerEntityManagerFactoryBean factory;

    private Database(String url, boolean echoSql) {
        this.url = url;
        this.echoSql = echoSql;
        this.pool = pool(url);
    }

    /** Open the database at {@code url}. Nothing connects until something is asked. */
    public static Database open(String url) {
        return open(url, false);
    }

    /** Open the database at {@code url}, writing every statement to the log when asked. */
    public static Database open(String url, boolean echoSql) {
        return new Database(url, echoSql);
    }

    /** A private in memory database that survives more than one connection and shares nothing. */
    public static Database inMemory() {
        return open("jdbc:h2:mem:jeepdolly-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    }

    /** The URL as the driver gets it: H2 is told the schema's column names are not keywords. */
    public static String jdbcUrl(String configured) {
        if (configured.startsWith("jdbc:h2:")
                && !configured.toUpperCase(Locale.ROOT).contains("NON_KEYWORDS")) {
            return configured + ";" + H2_NON_KEYWORDS;
        }
        return configured;
    }

    private static HikariDataSource pool(String url) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl(url));
        config.setPoolName("jeepdolly");
        config.setMaximumPoolSize(4);
        config.setMinimumIdle(1);
        config.setInitializationFailTimeout(-1);
        return new HikariDataSource(config);
    }

    /**
     * An entity manager factory over {@code dataSource} for the office's tables.
     *
     * @param ddl what Hibernate does to the schema at start up: {@code validate} in service, {@code
     *     create} only to build a schema straight from the mapping for comparison
     */
    public static LocalContainerEntityManagerFactoryBean entityManagerFactoryBean(
            DataSource dataSource, boolean echoSql, String ddl) {
        LocalContainerEntityManagerFactoryBean bean = new LocalContainerEntityManagerFactoryBean();
        bean.setDataSource(dataSource);
        bean.setPackagesToScan(MAPPED_PACKAGE);
        bean.setPersistenceUnitName("jeepdolly");
        bean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("hibernate.hbm2ddl.auto", ddl);
        properties.put("hibernate.show_sql", String.valueOf(echoSql));
        properties.put("hibernate.default_batch_fetch_size", "64");
        // Hibernate otherwise lays columns out by type size, and a schema it builds stops
        // matching the order the migrations declare.
        properties.put("hibernate.column_ordering_strategy", "legacy");
        bean.setJpaPropertyMap(properties);
        return bean;
    }

    /** The URL as configured, which is what the command line prints. */
    public String url() {
        return url;
    }

    /** Whether this database vanishes when it is closed. */
    public boolean isInMemory() {
        return url.startsWith("jdbc:h2:mem:");
    }

    public DataSource dataSource() {
        return pool;
    }

    /** The factory, built and validated against the schema the first time it is asked for. */
    public synchronized EntityManagerFactory entityManagerFactory() {
        if (factory == null) {
            factory = entityManagerFactoryBean(pool, echoSql, "validate");
            factory.afterPropertiesSet();
        }
        return factory.getObject();
    }

    /** A fresh unit of work with its transaction begun. Nothing sticks unless it commits. */
    public UnitOfWork unitOfWork() {
        SessionFactory sessions = entityManagerFactory().unwrap(SessionFactory.class);
        Session session =
                sessions.withOptions().interceptor(new ChangeTracker(listeners)).openSession();
        return new UnitOfWork(session);
    }

    /**
     * Hear about every successful flush of every unit of work this database opens from now on.
     *
     * @return the listener, for {@link #stopListening}
     */
    public FlushListener listen(FlushListener listener) {
        listeners.add(listener);
        return listener;
    }

    /** Stop telling {@code listener} about flushes. */
    public void stopListening(FlushListener listener) {
        listeners.remove(listener);
    }

    /** Put the schema in a database that has none, or bring an old one up to date. */
    public void migrate() {
        flyway().migrate();
    }

    /** Take the schema out. Everything in it goes with it. */
    public synchronized void drop() {
        closeFactory();
        flyway().clean();
    }

    /** The tables in the database, lower case and sorted, the migrations' own bookkeeping aside. */
    public List<String> tableNames() {
        try (Connection connection = pool.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            TreeSet<String> names = new TreeSet<>();
            try (ResultSet tables = metadata.getTables(null, null, "%", new String[] {"TABLE"})) {
                while (tables.next()) {
                    String schema = tables.getString("TABLE_SCHEM");
                    if (schema != null && schema.equalsIgnoreCase("INFORMATION_SCHEMA")) {
                        continue;
                    }
                    names.add(tables.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
                }
            }
            names.remove(HISTORY_TABLE);
            return List.copyOf(names);
        } catch (SQLException trouble) {
            throw new IllegalStateException("cannot list the tables of " + url, trouble);
        }
    }

    /**
     * Close what this database opened. An in memory database is shut down, so it vanishes the way
     * the original's in memory engine did. Safe to call twice.
     */
    @Override
    public synchronized void close() {
        closeFactory();
        if (pool.isClosed()) {
            return;
        }
        pool.close();
        if (isInMemory()) {
            try (Connection connection = DriverManager.getConnection(jdbcUrl(url));
                    Statement statement = connection.createStatement()) {
                statement.execute("SHUTDOWN");
            } catch (SQLException trouble) {
                throw new IllegalStateException("cannot shut down " + url, trouble);
            }
        }
    }

    private void closeFactory() {
        if (factory != null) {
            factory.destroy();
            factory = null;
        }
    }

    private Flyway flyway() {
        return Flyway.configure()
                .dataSource(pool)
                .locations(MIGRATIONS)
                .cleanDisabled(false)
                .load();
    }
}
