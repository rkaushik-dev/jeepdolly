package io.github.rkaushikdev.jeepdolly.persistence;

import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import io.github.rkaushikdev.jeepdolly.SeasonBook;
import io.github.rkaushikdev.jeepdolly.SeasonOrder;
import io.github.rkaushikdev.jeepdolly.persistence.models.SeasonOrderRow;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Reading and writing seasonal orders, and building the book the router reads.
 *
 * <p>Filing an order runs the season book's own overlap check first, so the rule that two orders
 * may not overlap is enforced by the same code whether the order arrives over the counter or out of
 * a fixture. The book is rebuilt for each request: there are perhaps thirty orders in a year, and
 * the alternative is a cache that is stale on the one afternoon of the year it matters.
 */
public final class SeasonStore {

    private SeasonStore() {}

    /** Every order on file, as the book the routing reads. */
    public static SeasonBook bookFrom(EntityManager entityManager) {
        SeasonBook book = new SeasonBook();
        for (SeasonOrderRow row : entityManager
                .createQuery("select o from SeasonOrderRow o order by o.starts, o.number", SeasonOrderRow.class)
                .getResultList()) {
            book.file(row.toDomain());
        }
        return book;
    }

    /** File one order signed by nobody in particular, refusing an overlap first. */
    public static SeasonOrderRow fileOrder(EntityManager entityManager, SeasonOrder order) {
        return fileOrder(entityManager, order, "", "");
    }

    /**
     * File one order, refusing an overlap before anything is written.
     *
     * @throws io.github.rkaushikdev.jeepdolly.Conflict when the number is taken or the order
     *     overlaps another in its region
     */
    public static SeasonOrderRow fileOrder(
            EntityManager entityManager, SeasonOrder order, String signedBy, String note) {
        bookFrom(entityManager).file(order);
        SeasonOrderRow row = SeasonOrderRow.of(order, signedBy, note);
        entityManager.persist(row);
        return row;
    }

    /** @throws NotOnFile when no order with that number is on file */
    public static SeasonOrderRow rowFor(EntityManager entityManager, String number) {
        SeasonOrderRow found = entityManager.find(SeasonOrderRow.class, number);
        if (found == null) {
            throw new NotOnFile("no order " + number, Facts.of("order", number));
        }
        return found;
    }

    /**
     * End an order early. The row stays, with the day it lifted on it; an order lifted before it
     * started ends where it began.
     */
    public static SeasonOrderRow lift(EntityManager entityManager, String number, LocalDate on) {
        SeasonOrderRow row = rowFor(entityManager, number);
        row.setEnds(on.isBefore(row.getStarts()) ? row.getStarts() : on);
        return row;
    }

    /** The order in force in a region on a day, if any is. */
    public static Optional<SeasonOrderRow> inForce(EntityManager entityManager, String region, LocalDate day) {
        return entityManager
                .createQuery(
                        "select o from SeasonOrderRow o where o.region = :region and o.starts <= :day"
                                + " and o.ends >= :day",
                        SeasonOrderRow.class)
                .setParameter("region", region)
                .setParameter("day", day)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
    }

    /** The orders of one region, in the order they start. */
    public static List<SeasonOrderRow> forRegion(EntityManager entityManager, String region) {
        return entityManager
                .createQuery(
                        "select o from SeasonOrderRow o where o.region = :region order by o.starts",
                        SeasonOrderRow.class)
                .setParameter("region", region)
                .getResultList();
    }
}
