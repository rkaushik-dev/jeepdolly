package io.github.rkaushikdev.jeepdolly.api;

import io.github.rkaushikdev.jeepdolly.persistence.Database;
import io.github.rkaushikdev.jeepdolly.persistence.FlushListener;
import io.github.rkaushikdev.jeepdolly.persistence.Reference;
import io.github.rkaushikdev.jeepdolly.persistence.ReferenceCache;

/**
 * The application's cache, kept honest for as long as the application runs: any flush that writes
 * a published table empties it, and stopping the application stops the watching, because the
 * database it watches may outlive the application.
 */
final class ReferenceWatch implements AutoCloseable {

    private final Database database;
    private final FlushListener listener;

    ReferenceWatch(Database database, ReferenceCache cache) {
        this.database = database;
        this.listener = Reference.watch(database, cache);
    }

    @Override
    public void close() {
        database.stopListening(listener);
    }
}
