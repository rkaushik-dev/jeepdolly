package io.github.rkaushikdev.jeepdolly.persistence;

import io.github.rkaushikdev.jeepdolly.Conflict;
import io.github.rkaushikdev.jeepdolly.persistence.repositories.CarrierRepository;
import io.github.rkaushikdev.jeepdolly.persistence.repositories.CitationRepository;
import io.github.rkaushikdev.jeepdolly.persistence.repositories.CommodityRepository;
import io.github.rkaushikdev.jeepdolly.persistence.repositories.LineRepository;
import io.github.rkaushikdev.jeepdolly.persistence.repositories.NetworkRepository;
import io.github.rkaushikdev.jeepdolly.persistence.repositories.PilotRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.PersistenceException;

/**
 * One persistence context, one transaction, and the repositories over it.
 *
 * <p>Everything that changes the database goes through this. A caller opens one, does its work
 * through the repositories it hands out, and commits. If it does not commit, nothing happened:
 * closing without a commit rolls back, which is also what an exception on the way out of a
 * try-with-resources block does, without anybody having to remember to.
 *
 * <pre>{@code
 * try (UnitOfWork work = database.unitOfWork()) {
 *     work.carriers().add(carrier);
 *     work.commit();
 * }
 * }</pre>
 *
 * <p>The repositories share the entity manager, so anything one of them writes is visible to the
 * next without a commit, and the permit number counter takes its number inside the same
 * transaction as the permit that carries it. A commit begins the next transaction straight away
 * and keeps every row it wrote readable, so work can carry on in the same unit.
 *
 * <p>The permit tables' repositories (applications, permits, reprints) hang off here too once the
 * permit documents they map are in place.
 */
public final class UnitOfWork implements AutoCloseable {

    private final EntityManager entityManager;
    private boolean open = true;

    /** A unit of work over an entity manager somebody opened, its transaction begun here. */
    public UnitOfWork(EntityManager entityManager) {
        this.entityManager = entityManager;
        entityManager.getTransaction().begin();
    }

    /**
     * The open entity manager, for the few callers that write rows directly.
     *
     * <p>The audit trail is the reason this exists. Everything else goes through a repository, and
     * a caller reaching for this to run a query is a caller that wants a repository method instead.
     *
     * @throws Conflict when the unit of work has been closed
     */
    public EntityManager entityManager() {
        return require();
    }

    public CarrierRepository carriers() {
        return new CarrierRepository(require());
    }

    public CommodityRepository commodities() {
        return new CommodityRepository(require());
    }

    public NetworkRepository network() {
        return new NetworkRepository(require());
    }

    public CitationRepository citations() {
        return new CitationRepository(require());
    }

    public LineRepository lines() {
        return new LineRepository(require());
    }

    public PilotRepository pilots() {
        return new PilotRepository(require());
    }

    /** The permit number counter, inside this transaction. */
    public DatabaseCounter counter() {
        return new DatabaseCounter(require());
    }

    /**
     * Hand a new row to the persistence context; it is written at the next flush or commit.
     *
     * @throws IntegrityViolation when a row with the same key is already in this unit of work
     */
    public <T> T add(T row) {
        EntityManager opened = require();
        try {
            opened.persist(row);
        } catch (PersistenceException trouble) {
            throw translated(trouble);
        }
        return row;
    }

    /** Take a row out at the next flush. */
    public void delete(Object row) {
        require().remove(row);
    }

    /**
     * Write what is pending without committing it.
     *
     * @throws IntegrityViolation when the database refuses a row on one of its constraints
     */
    public void flush() {
        EntityManager opened = require();
        try {
            opened.flush();
        } catch (PersistenceException trouble) {
            throw translated(trouble);
        }
    }

    /**
     * Make the work so far stick, and begin the next transaction.
     *
     * @throws IntegrityViolation when the database refuses a row on one of its constraints; the
     *     work is rolled back and the unit stays open for more
     */
    public void commit() {
        EntityManager opened = require();
        EntityTransaction transaction = opened.getTransaction();
        try {
            transaction.commit();
        } catch (PersistenceException trouble) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            opened.clear();
            transaction.begin();
            throw translated(trouble);
        }
        transaction.begin();
    }

    /** Undo the work since the last commit, forget what was read, and begin again. */
    public void rollback() {
        EntityManager opened = require();
        EntityTransaction transaction = opened.getTransaction();
        if (transaction.isActive()) {
            transaction.rollback();
        }
        opened.clear();
        transaction.begin();
    }

    /** Whether the unit of work can still be used. */
    public boolean isOpen() {
        return open;
    }

    /** Roll back whatever was not committed and close. Safe to call twice. */
    @Override
    public void close() {
        if (!open) {
            return;
        }
        open = false;
        try {
            EntityTransaction transaction = entityManager.getTransaction();
            if (transaction.isActive()) {
                transaction.rollback();
            }
        } finally {
            entityManager.close();
        }
    }

    private EntityManager require() {
        if (!open) {
            throw new Conflict("this unit of work is not open");
        }
        return entityManager;
    }

    private static RuntimeException translated(PersistenceException trouble) {
        if (IntegrityViolation.isViolation(trouble)) {
            return new IntegrityViolation("the database refused a row: " + rootMessage(trouble), trouble);
        }
        return trouble;
    }

    private static String rootMessage(Throwable trouble) {
        Throwable root = trouble;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage();
    }
}
