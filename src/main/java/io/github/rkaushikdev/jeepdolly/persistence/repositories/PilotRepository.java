package io.github.rkaushikdev.jeepdolly.persistence.repositories;

import io.github.rkaushikdev.jeepdolly.Conflict;
import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import io.github.rkaushikdev.jeepdolly.escorts.Company;
import io.github.rkaushikdev.jeepdolly.escorts.Operator;
import io.github.rkaushikdev.jeepdolly.persistence.models.PilotCardRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.PilotCompanyRow;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;

/**
 * Pilot car companies and the cards the office has issued.
 *
 * <p>A card is never edited except to be suspended or lifted. A renewal is a new card, which is why
 * there is nothing here that moves an expiry date.
 */
public final class PilotRepository {

    private final EntityManager entityManager;

    public PilotRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** The entity manager this repository asks its questions of. */
    public EntityManager entityManager() {
        return entityManager;
    }

    /** @throws NotOnFile when no company with that ident is on file */
    public PilotCompanyRow companyRow(String ident) {
        PilotCompanyRow found = entityManager.find(PilotCompanyRow.class, ident);
        if (found == null) {
            throw new NotOnFile("no pilot car company " + ident, Facts.of("ident", ident));
        }
        return found;
    }

    /** @throws NotOnFile when no card with that number was issued */
    public PilotCardRow cardRow(String card) {
        PilotCardRow found = entityManager.find(PilotCardRow.class, card);
        if (found == null) {
            throw new NotOnFile("no pilot car card " + card, Facts.of("card", card));
        }
        return found;
    }

    /** Every company, by ident. */
    public List<PilotCompanyRow> companies() {
        return entityManager
                .createQuery("select c from PilotCompanyRow c order by c.ident", PilotCompanyRow.class)
                .getResultList();
    }

    /** Every card, by card number. */
    public List<PilotCardRow> cards() {
        return entityManager
                .createQuery("select c from PilotCardRow c order by c.card", PilotCardRow.class)
                .getResultList();
    }

    /** One company's cards, by card number. */
    public List<PilotCardRow> cards(String company) {
        return entityManager
                .createQuery(
                        "select c from PilotCardRow c where c.companyIdent = :company order by c.card",
                        PilotCardRow.class)
                .setParameter("company", company)
                .getResultList();
    }

    /** @throws Conflict when a company with that ident is already on file */
    public PilotCompanyRow addCompany(Company company) {
        if (entityManager.find(PilotCompanyRow.class, company.ident()) != null) {
            throw new Conflict("pilot car company " + company.ident() + " is already on file");
        }
        PilotCompanyRow row = PilotCompanyRow.of(company);
        entityManager.persist(row);
        return row;
    }

    /**
     * Issue a card, refusing one whose company is not on file.
     *
     * @throws Conflict when the card number is taken
     * @throws NotOnFile when the company is not on file
     */
    public PilotCardRow certify(Operator operator) {
        if (entityManager.find(PilotCardRow.class, operator.card()) != null) {
            throw new Conflict("pilot car card " + operator.card() + " is already on file");
        }
        companyRow(operator.company());
        PilotCardRow row = PilotCardRow.of(operator);
        entityManager.persist(row);
        return row;
    }

    /** Suspend a company. */
    public PilotCompanyRow suspendCompany(String ident) {
        return suspendCompany(ident, true);
    }

    /** Suspend a company, or lift its suspension. */
    public PilotCompanyRow suspendCompany(String ident, boolean suspended) {
        PilotCompanyRow row = companyRow(ident);
        row.setSuspended(suspended);
        return row;
    }

    /** Suspend a card. */
    public PilotCardRow suspendCard(String card) {
        return suspendCard(card, true);
    }

    /** Suspend a card, or lift its suspension. */
    public PilotCardRow suspendCard(String card, boolean suspended) {
        PilotCardRow row = cardRow(card);
        row.setSuspended(suspended);
        return row;
    }

    /** Cards in good standing running out between two days, soonest first. */
    public List<PilotCardRow> expiring(LocalDate start, LocalDate end) {
        return entityManager
                .createQuery(
                        "select c from PilotCardRow c where c.suspended = false and c.expires >= :start"
                                + " and c.expires <= :end order by c.expires, c.card",
                        PilotCardRow.class)
                .setParameter("start", start)
                .setParameter("end", end)
                .getResultList();
    }
}
