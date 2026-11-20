package io.github.rkaushikdev.jeepdolly.persistence.repositories;

import io.github.rkaushikdev.jeepdolly.Conflict;
import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import io.github.rkaushikdev.jeepdolly.enforcement.Citation;
import io.github.rkaushikdev.jeepdolly.enforcement.CitationState;
import io.github.rkaushikdev.jeepdolly.enforcement.Penalty;
import io.github.rkaushikdev.jeepdolly.persistence.models.CitationRow;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;

/**
 * Citations, by number, by carrier and by what is still owing.
 *
 * <p>The prior count is the interesting question. It drives the repeat multiplier, and it counts
 * only citations that stuck: a dismissed one and a voided one are not prior offences, because the
 * carrier was found not to have committed them. A contested one that has not been heard yet does
 * count, which is the office's reading of the statute and the one thing here a lawyer has ever
 * argued with.
 */
public final class CitationRepository {

    private final EntityManager entityManager;

    public CitationRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** The entity manager this repository asks its questions of. */
    public EntityManager entityManager() {
        return entityManager;
    }

    /** @throws NotOnFile when no citation with that number is on file */
    public CitationRow row(String number) {
        CitationRow found = entityManager.find(CitationRow.class, number);
        if (found == null) {
            throw new NotOnFile("no citation " + number, Facts.of("number", number));
        }
        return found;
    }

    public Citation get(String number) {
        return row(number).toDomain();
    }

    /**
     * File a citation assessed under the penalty schedule of a day.
     *
     * @throws Conflict when the number is already on file
     */
    public CitationRow add(Citation citation, LocalDate schedule) {
        if (entityManager.find(CitationRow.class, citation.number()) != null) {
            throw new Conflict("citation " + citation.number() + " is already on file");
        }
        CitationRow row = CitationRow.of(citation, schedule);
        entityManager.persist(row);
        return row;
    }

    /** Write the state back, leaving the closing reason as it was. */
    public CitationRow saveState(Citation citation) {
        return saveState(citation, "");
    }

    /** Write the state back, and the reason when one is given for a closing state. */
    public CitationRow saveState(Citation citation, String reason) {
        CitationRow row = row(citation.number());
        row.setState(citation.state());
        if (!reason.isEmpty()) {
            row.setClosingReason(reason);
        }
        return row;
    }

    /** Everything written against one carrier, newest first. */
    public List<CitationRow> forCarrier(int usdot) {
        return entityManager
                .createQuery(
                        "select c from CitationRow c where c.usdot = :usdot order by c.written desc, c.number",
                        CitationRow.class)
                .setParameter("usdot", usdot)
                .getResultList();
    }

    /** Everything still expecting money, oldest first, which is the chase list. */
    public List<CitationRow> owing() {
        return entityManager
                .createQuery(
                        "select c from CitationRow c where c.state in :owing order by c.written, c.number",
                        CitationRow.class)
                .setParameter("owing", List.of(CitationState.WRITTEN, CitationState.CONTESTED, CitationState.UPHELD))
                .getResultList();
    }

    /** The citations written in a window, both ends included, oldest first. */
    public List<CitationRow> writtenBetween(LocalDate start, LocalDate end) {
        return entityManager
                .createQuery(
                        "select c from CitationRow c where c.written >= :start and c.written <= :end"
                                + " order by c.written, c.number",
                        CitationRow.class)
                .setParameter("start", start)
                .setParameter("end", end)
                .getResultList();
    }

    /** How many citations stuck against this carrier in the usual repeat window. */
    public int priors(int usdot, LocalDate before) {
        return priors(usdot, before, Penalty.REPEAT_DAYS);
    }

    /**
     * How many citations stuck against this carrier in the window ending the day before.
     *
     * <p>A citation written today is never a prior offence against itself, and two written on the
     * same day are both first offences: the officer wrote them a minute apart and the carrier had no
     * chance to mend anything in between.
     */
    public int priors(int usdot, LocalDate before, int withinDays) {
        LocalDate opened = before.minusDays(withinDays);
        return entityManager
                .createQuery(
                        "select c from CitationRow c where c.usdot = :usdot and c.written >= :opened"
                                + " and c.written < :before and c.state not in :cleared",
                        CitationRow.class)
                .setParameter("usdot", usdot)
                .setParameter("opened", opened)
                .setParameter("before", before)
                .setParameter("cleared", List.of(CitationState.DISMISSED, CitationState.VOID))
                .getResultList()
                .size();
    }

    public long count() {
        return entityManager
                .createQuery("select count(c) from CitationRow c", Long.class)
                .getSingleResult();
    }
}
