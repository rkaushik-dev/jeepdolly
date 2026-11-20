package io.github.rkaushikdev.jeepdolly.api;

import io.github.rkaushikdev.jeepdolly.OfficeRules;
import io.github.rkaushikdev.jeepdolly.permits.Numbering;
import io.github.rkaushikdev.jeepdolly.permits.Office;
import io.github.rkaushikdev.jeepdolly.persistence.Books;
import io.github.rkaushikdev.jeepdolly.persistence.Reference;
import io.github.rkaushikdev.jeepdolly.persistence.ReferenceCache;
import io.github.rkaushikdev.jeepdolly.persistence.SeasonStore;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.routing.TurnBook;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.Optional;

/**
 * What a request is given: an office built from what is on file, for the right day.
 *
 * <p>The office is assembled per request, but not from scratch. The published books come out of a
 * cache keyed on the day, emptied by any flush that touches a published table, so an office cannot
 * serve a road log without a segment somebody added a moment ago. What is deliberately not cached
 * is the restriction book, the carrier record, the seasonal orders and the enforcement accounts.
 * Those change during the day, and a permit written against yesterday's closure is exactly the
 * failure this office cannot have.
 *
 * <p>The reading itself is in the persistence layer, where a command line report can reach it.
 * What is here is the choosing: which day, and which cache.
 */
public final class Deps {

    private Deps() {}

    /** The office as it stands today, read fresh, under the default rules. */
    public static Office officeFrom(UnitOfWork work) {
        return officeFrom(work, LocalDate.now());
    }

    /** The office as it stands on a day, read fresh, under the default rules. */
    public static Office officeFrom(UnitOfWork work, LocalDate on) {
        return officeFrom(work, on, Optional.empty(), OfficeRules.defaults());
    }

    /**
     * Assemble the office from what is on file on a day.
     *
     * <p>The published books come out of the cache when there is one and are read fresh when there
     * is not. Everything else is read every time, whether or not a cache was passed. The permit
     * number counter is the one in the database, inside the caller's transaction, so a number taken
     * and not committed is handed out again.
     *
     * @param cache the published book cache of the process, if it keeps one
     * @param rules the office rules in force, which set the citation grace
     */
    public static Office officeFrom(UnitOfWork work, LocalDate on, Optional<ReferenceCache> cache, OfficeRules rules) {
        EntityManager db = work.entityManager();
        Reference published = cache.map(books -> books.books(db, on)).orElseGet(() -> Reference.read(db, on));
        return Office.builder()
                .network(published.network())
                .restrictions(Books.restrictionsFrom(db))
                .seasons(SeasonStore.bookFrom(db))
                .statutes(published.statutes())
                .sizes(published.sizes())
                .commodities(published.commodities())
                .carriers(Books.carriersFrom(db, on))
                .structures(published.structures())
                // The card index of corners has only ever lived in the code. It moves perhaps
                // twice a year and nobody has asked to edit it on a screen.
                .turns(TurnBook.coded())
                .escorts(Books.escortsFrom(db))
                .enforcement(Books.enforcementFrom(db, rules.citationGrace()))
                .lines(published.lines())
                .schedules(published.schedules())
                .library(published.library())
                .numbering(new Numbering(work.counter()))
                .build();
    }
}
