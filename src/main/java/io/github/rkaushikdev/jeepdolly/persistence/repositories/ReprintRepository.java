package io.github.rkaushikdev.jeepdolly.persistence.repositories;

import io.github.rkaushikdev.jeepdolly.Conflict;
import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.permits.Reprint;
import io.github.rkaushikdev.jeepdolly.persistence.models.ReprintRow;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;

/** The reprint register, which is a list of copies beside each permit. */
public final class ReprintRepository {

    private final EntityManager entityManager;

    public ReprintRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** The entity manager this repository asks its questions of. */
    public EntityManager entityManager() {
        return entityManager;
    }

    /** Every copy printed of one permit, by copy number. */
    public List<ReprintRow> forPermit(String number) {
        return entityManager
                .createQuery("select r from ReprintRow r where r.permit = :number order by r.copy", ReprintRow.class)
                .setParameter("number", number)
                .getResultList();
    }

    /** Every copy number ever issued for a permit, for working out the next one. */
    public List<Integer> copiesTaken(String number) {
        return forPermit(number).stream().map(ReprintRow::getCopy).toList();
    }

    /**
     * Record a copy, refusing a number that has already been handed out.
     *
     * <p>The database would refuse it too, on the key. This says conflict first so that two clerks
     * racing each other get a sentence rather than a constraint failure.
     *
     * @throws Conflict when that copy of that permit has already been printed
     */
    public ReprintRow add(Reprint reprint) {
        if (entityManager.find(ReprintRow.class, new ReprintRow.Key(reprint.permit(), reprint.copy())) != null) {
            throw new Conflict(
                    "copy " + reprint.copy() + " of " + reprint.permit() + " has already been printed",
                    Facts.of("permit", reprint.permit(), "copy", reprint.copy()));
        }
        ReprintRow row = ReprintRow.of(reprint);
        entityManager.persist(row);
        return row;
    }

    /** Every copy issued in a window, both ends included, which is the yearly question. */
    public List<ReprintRow> between(LocalDate start, LocalDate end) {
        return entityManager
                .createQuery(
                        "select r from ReprintRow r where r.issuedOn >= :start and r.issuedOn <= :end"
                                + " order by r.issuedOn, r.permit, r.copy",
                        ReprintRow.class)
                .setParameter("start", start)
                .setParameter("end", end)
                .getResultList();
    }

    public long count() {
        return entityManager
                .createQuery("select count(r) from ReprintRow r", Long.class)
                .getSingleResult();
    }
}
