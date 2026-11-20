package io.github.rkaushikdev.jeepdolly.api;

import io.github.rkaushikdev.jeepdolly.OfficeRules;
import io.github.rkaushikdev.jeepdolly.Settings;
import io.github.rkaushikdev.jeepdolly.permits.Office;
import io.github.rkaushikdev.jeepdolly.persistence.Database;
import io.github.rkaushikdev.jeepdolly.persistence.ReferenceCache;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import java.time.LocalDate;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * What a route is given: one unit of work per request, and an office built from it.
 *
 * <p>The unit of work is committed if the handler returns and rolled back if it throws. Handlers do
 * not commit, and nothing commits on the way out by accident: a refusal after a number was taken
 * leaves the counter where it was. The commit happens before the handler's answer is written,
 * which is why this is a call around the handler's body and not an interceptor.
 *
 * <p>The office is assembled per request through {@link Deps#officeFrom}, with the published books
 * from the application's own cache when it has one and the office rules from the settings the
 * application was started with.
 */
public final class Work {

    private final Database database;
    private final Optional<ReferenceCache> cache;
    private final Settings settings;

    public Work(Database database, Optional<ReferenceCache> cache, Settings settings) {
        this.database = database;
        this.cache = cache;
        this.settings = settings;
    }

    /** Run {@code handler} in a fresh unit of work and answer what it answers. */
    public <T> T apply(Function<UnitOfWork, T> handler) {
        try (UnitOfWork work = database.unitOfWork()) {
            T answer = handler.apply(work);
            work.commit();
            return answer;
        }
    }

    /** Run {@code handler} in a fresh unit of work, for a route that answers with no body. */
    public void run(Consumer<UnitOfWork> handler) {
        apply(work -> {
            handler.accept(work);
            return null;
        });
    }

    /** The office as it stands today, for a route that needs to decide something. */
    public Office office(UnitOfWork work) {
        return office(work, LocalDate.now());
    }

    /** The office as it stands on a day. */
    public Office office(UnitOfWork work, LocalDate on) {
        return Deps.officeFrom(work, on, cache, settings.rules());
    }

    /** The office rules this application was started with. */
    public OfficeRules rules() {
        return settings.rules();
    }

    /** The settings this application was started with. */
    public Settings settings() {
        return settings;
    }

    /** The published book cache, when the application keeps one. */
    public Optional<ReferenceCache> cache() {
        return cache;
    }
}
