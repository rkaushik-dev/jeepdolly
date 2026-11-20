package io.github.rkaushikdev.jeepdolly.persistence.repositories;

import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import io.github.rkaushikdev.jeepdolly.permits.Permit;
import io.github.rkaushikdev.jeepdolly.permits.PermitNumber;
import io.github.rkaushikdev.jeepdolly.permits.PermitState;
import io.github.rkaushikdev.jeepdolly.persistence.Mappers;
import io.github.rkaushikdev.jeepdolly.persistence.models.PermitRow;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;

/** Permits, by number and by the four questions the telephone asks. */
public final class PermitRepository {

    private final EntityManager entityManager;

    public PermitRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** The entity manager this repository asks its questions of. */
    public EntityManager entityManager() {
        return entityManager;
    }

    /** @throws NotOnFile when no permit with that number is on file */
    public PermitRow row(String number) {
        PermitRow found = entityManager.find(PermitRow.class, number);
        if (found == null) {
            throw new NotOnFile("no permit " + number, Facts.of("number", number));
        }
        return found;
    }

    public Permit get(String number) {
        return Mappers.rowToPermit(row(number));
    }

    /** Put a permit on file, with the conditions and fee lines it was printed with. */
    public PermitRow add(Permit permit) {
        PermitRow row = Mappers.permitToRow(permit);
        entityManager.persist(row);
        return row;
    }

    /** Write the state back, with the reason it was voided and what replaced it. */
    public PermitRow saveState(Permit permit) {
        PermitRow row = row(permit.number().toString());
        row.setState(permit.state());
        row.setVoidReason(permit.voidReason());
        row.setSupersededBy(permit.supersededBy().map(PermitNumber::toString).orElse(null));
        return row;
    }

    /** Every issued permit good for travel on a day, by number. */
    public List<PermitRow> onTheRoad(LocalDate day) {
        return entityManager
                .createQuery(
                        "select p from PermitRow p where p.state = :issued and p.validFrom <= :day"
                                + " and p.validTo >= :day order by p.number",
                        PermitRow.class)
                .setParameter("issued", PermitState.ISSUED)
                .setParameter("day", day)
                .getResultList();
    }

    /** The permits issued in a window, both ends included, oldest first. */
    public List<PermitRow> issuedBetween(LocalDate start, LocalDate end) {
        return entityManager
                .createQuery(
                        "select p from PermitRow p where p.issued >= :start and p.issued <= :end"
                                + " order by p.issued, p.number",
                        PermitRow.class)
                .setParameter("start", start)
                .setParameter("end", end)
                .getResultList();
    }

    /** One carrier's permits, newest first. */
    public List<PermitRow> forCarrier(int usdot) {
        return entityManager
                .createQuery(
                        "select p from PermitRow p where p.usdot = :usdot order by p.issued desc, p.number desc",
                        PermitRow.class)
                .setParameter("usdot", usdot)
                .getResultList();
    }

    /** Issued permits whose last day falls between two days, soonest first. */
    public List<PermitRow> expiring(LocalDate day, LocalDate horizon) {
        return entityManager
                .createQuery(
                        "select p from PermitRow p where p.state = :issued and p.validTo >= :day"
                                + " and p.validTo <= :horizon order by p.validTo, p.number",
                        PermitRow.class)
                .setParameter("issued", PermitState.ISSUED)
                .setParameter("day", day)
                .setParameter("horizon", horizon)
                .getResultList();
    }

    /**
     * Every permit whose route names a segment, for a closure notice.
     *
     * <p>The route is one column of idents, so this narrows with a pattern and then checks the
     * idents themselves, which keeps {@code US2-1} from matching a route over {@code US2-10}.
     * Closures are rare and the office wants the answer rather than a schema change.
     */
    public List<PermitRow> touchingSegment(String ident) {
        return entityManager
                .createQuery("select p from PermitRow p where p.route like :pattern", PermitRow.class)
                .setParameter("pattern", "%" + ident + "%")
                .getResultList()
                .stream()
                .filter(one -> one.getRouteIdents().contains(ident))
                .toList();
    }
}
