package io.github.rkaushikdev.jeepdolly.cli;

import io.github.rkaushikdev.jeepdolly.Settings;
import io.github.rkaushikdev.jeepdolly.persistence.Database;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;

/**
 * What every command is handed: the settings and a way to open work.
 *
 * <p>A console either opens its database itself, the first time a command asks for it, or is handed
 * one somebody else already opened. A caller that already has a database keeps it: that is how the
 * tests run against the suite's database, and how a long running process can run a command without
 * opening a second connection pool. Closing the console closes only a database it opened itself.
 */
public final class Console implements AutoCloseable {

    private final Settings settings;
    private final boolean handed;
    private Database database;

    /** A console that opens the database the settings name, when a command first asks for it. */
    public Console(Settings settings) {
        this.settings = settings;
        this.handed = false;
    }

    /** A console over a database somebody else opened and will close. */
    public Console(Settings settings, Database database) {
        this.settings = settings;
        this.database = database;
        this.handed = true;
    }

    /** The settings the console was built with. */
    public Settings settings() {
        return settings;
    }

    /** The database, opened the first time it is asked for and the same one every time after. */
    public synchronized Database database() {
        if (database == null) {
            database = Database.open(settings.databaseUrl(), settings.echoSql());
        }
        return database;
    }

    /** A fresh unit of work. Nothing sticks unless the command commits. */
    public UnitOfWork work() {
        return database().unitOfWork();
    }

    /** Put the schema in the database, or bring it up to date. Running it twice changes nothing. */
    public void build() {
        database().migrate();
    }

    /** Close the database if this console opened it. One that was handed in is left open. */
    @Override
    public synchronized void close() {
        if (!handed && database != null) {
            database.close();
        }
    }
}
