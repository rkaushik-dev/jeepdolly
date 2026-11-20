package io.github.rkaushikdev.jeepdolly.persistence.repositories;

import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import io.github.rkaushikdev.jeepdolly.domain.CommodityBook;
import io.github.rkaushikdev.jeepdolly.permits.Application;
import io.github.rkaushikdev.jeepdolly.permits.ApplicationState;
import io.github.rkaushikdev.jeepdolly.persistence.Mappers;
import io.github.rkaushikdev.jeepdolly.persistence.models.ApplicationRow;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;

/**
 * Applications, by reference and by the queues the counter works through.
 *
 * <p>An application reads back against a commodity book, because each piece on the trailer points
 * at a line of the commodity table.
 */
public final class ApplicationRepository {

    private final EntityManager entityManager;
    private final CommodityBook commodities;

    public ApplicationRepository(EntityManager entityManager, CommodityBook commodities) {
        this.entityManager = entityManager;
        this.commodities = commodities;
    }

    /** The entity manager this repository asks its questions of. */
    public EntityManager entityManager() {
        return entityManager;
    }

    /** The commodity book applications read back against. */
    public CommodityBook commodities() {
        return commodities;
    }

    /** @throws NotOnFile when no application with that reference is on file */
    public ApplicationRow row(String reference) {
        ApplicationRow found = entityManager.find(ApplicationRow.class, reference);
        if (found == null) {
            throw new NotOnFile("no application " + reference, Facts.of("reference", reference));
        }
        return found;
    }

    public Application get(String reference) {
        return Mappers.rowToApplication(row(reference), commodities);
    }

    /** Put an application on file, axles, equipment and pieces with it. */
    public ApplicationRow add(Application application) {
        ApplicationRow row = Mappers.applicationToRow(application);
        entityManager.persist(row);
        return row;
    }

    /** Write the state back onto the row. */
    public ApplicationRow saveState(Application application) {
        ApplicationRow row = row(application.reference());
        row.setState(application.state());
        return row;
    }

    /**
     * Write the named pilot cars back, which is the one column that moves.
     *
     * <p>Everything else about an application is written once and read after that. The cards move
     * because they are named late and changed twice, and rewriting the whole row for one column
     * would drag the axles and the pieces along with it.
     */
    public ApplicationRow saveEscortCards(Application application) {
        ApplicationRow row = row(application.reference());
        row.setEscortCards(String.join(",", application.escortCards()));
        return row;
    }

    /** The applications in one state, oldest filing first. */
    public List<ApplicationRow> inState(ApplicationState state) {
        return entityManager
                .createQuery(
                        "select a from ApplicationRow a where a.state = :state order by a.filed, a.reference",
                        ApplicationRow.class)
                .setParameter("state", state)
                .getResultList();
    }

    /** What the counter works through: everything submitted and not yet decided. */
    public List<ApplicationRow> waiting() {
        return inState(ApplicationState.SUBMITTED);
    }

    /** One carrier's applications, newest filing first. */
    public List<ApplicationRow> forCarrier(int usdot) {
        return entityManager
                .createQuery(
                        "select a from ApplicationRow a where a.usdot = :usdot order by a.filed desc",
                        ApplicationRow.class)
                .setParameter("usdot", usdot)
                .getResultList();
    }

    /** The applications filed in a window, both ends included, oldest first. */
    public List<ApplicationRow> filedBetween(LocalDate start, LocalDate end) {
        return entityManager
                .createQuery(
                        "select a from ApplicationRow a where a.filed >= :start and a.filed <= :end"
                                + " order by a.filed, a.reference",
                        ApplicationRow.class)
                .setParameter("start", start)
                .setParameter("end", end)
                .getResultList();
    }
}
