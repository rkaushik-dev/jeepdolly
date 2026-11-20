package io.github.rkaushikdev.jeepdolly.persistence;

import io.github.rkaushikdev.jeepdolly.Conflict;
import io.github.rkaushikdev.jeepdolly.permits.PermitNumber;
import io.github.rkaushikdev.jeepdolly.permits.Sequences;
import io.github.rkaushikdev.jeepdolly.permits.Series;
import io.github.rkaushikdev.jeepdolly.persistence.models.CounterRow;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;

/**
 * The permit number counter, as a row read under a lock inside the caller's transaction.
 *
 * <p>A permit number and the permit that carries it have to be written in one transaction. The
 * counter reads its row for update, increments it and writes it, all inside the unit of work, so a
 * crash between taking the number and writing the permit leaves the counter where it was and
 * nobody has to explain a missing number to accounting. That is why the counter is a table and not
 * a database sequence: a sequence hands out a number that is gone whether or not the transaction
 * commits.
 */
public final class DatabaseCounter implements Sequences {

    private final EntityManager entityManager;

    DatabaseCounter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * The next sequence for the year and series, used up inside this transaction.
     *
     * @throws Conflict when the series is full for the year, rather than wrapping
     */
    @Override
    public int take(int year, Series series) {
        CounterRow row = locked(year, series);
        if (row == null) {
            row = new CounterRow(year, series.value(), 1);
            entityManager.persist(row);
            entityManager.flush();
        }
        if (row.getNextSequence() > PermitNumber.LAST_SEQUENCE) {
            throw new Conflict("the " + series.value() + " series is full for " + year);
        }
        int taken = row.getNextSequence();
        row.setNextSequence(taken + 1);
        entityManager.flush();
        return taken;
    }

    /** The sequence the next take will hand out, without taking it. */
    public int peek(int year, Series series) {
        CounterRow row = entityManager.find(CounterRow.class, new CounterRow.Key(year, series.value()));
        return row == null ? 1 : row.getNextSequence();
    }

    @Override
    public int issued(int year, Series series) {
        return peek(year, series) - 1;
    }

    /** A whole single trip permit number for a day. */
    public PermitNumber number(LocalDate on) {
        return number(on, Series.SINGLE);
    }

    /** A whole permit number for a day in a series. */
    public PermitNumber number(LocalDate on, Series series) {
        return new PermitNumber(on.getYear(), series, take(on.getYear(), series));
    }

    private CounterRow locked(int year, Series series) {
        List<CounterRow> found = entityManager
                .createQuery("select c from CounterRow c where c.year = :year and c.series = :series", CounterRow.class)
                .setParameter("year", year)
                .setParameter("series", series.value())
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
        return found.isEmpty() ? null : found.get(0);
    }
}
