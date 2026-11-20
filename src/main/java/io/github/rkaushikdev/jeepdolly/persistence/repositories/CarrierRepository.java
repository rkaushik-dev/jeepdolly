package io.github.rkaushikdev.jeepdolly.persistence.repositories;

import io.github.rkaushikdev.jeepdolly.Conflict;
import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import io.github.rkaushikdev.jeepdolly.domain.Carrier;
import io.github.rkaushikdev.jeepdolly.persistence.models.CarrierRow;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Carriers, by number and by name: the questions the counter asks about the people it writes for.
 *
 * <p>Like every repository here it holds no state. The unit of work owns the entity manager and
 * the transaction, and a repository is a way of asking questions of whichever one it was handed.
 */
public final class CarrierRepository {

    private final EntityManager entityManager;

    public CarrierRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** The entity manager this repository asks its questions of. */
    public EntityManager entityManager() {
        return entityManager;
    }

    /** @throws NotOnFile when nobody registered that usdot number */
    public CarrierRow row(int usdot) {
        CarrierRow found = entityManager.find(CarrierRow.class, usdot);
        if (found == null) {
            throw new NotOnFile("no carrier with usdot " + usdot, Facts.of("usdot", usdot));
        }
        return found;
    }

    /** The carrier as of a day, with the paper in force then. */
    public Carrier get(int usdot, LocalDate on) {
        return row(usdot).toDomain(on);
    }

    /** Name search, the way the counter does it: any part, any case, by name. */
    public List<CarrierRow> search(String text) {
        String needle = text.strip().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return List.of();
        }
        return entityManager
                .createQuery("select c from CarrierRow c order by c.name", CarrierRow.class)
                .getResultList()
                .stream()
                .filter(one -> one.getName().toLowerCase(Locale.ROOT).contains(needle))
                .toList();
    }

    /**
     * File a new carrier, refusing a number that is already registered.
     *
     * <p>The database would refuse this too, at commit time, as a failure at the caller. The office
     * would rather say conflict, and say it before anything else in the transaction is done.
     *
     * @throws Conflict when the usdot number is already registered
     */
    public CarrierRow add(Carrier carrier) {
        if (entityManager.find(CarrierRow.class, carrier.usdot()) != null) {
            throw new Conflict(
                    "usdot " + carrier.usdot() + " is already registered", Facts.of("usdot", carrier.usdot()));
        }
        CarrierRow row = CarrierRow.of(carrier);
        entityManager.persist(row);
        return row;
    }

    public long count() {
        return entityManager
                .createQuery("select count(c) from CarrierRow c", Long.class)
                .getSingleResult();
    }
}
