package io.github.rkaushikdev.jeepdolly.persistence.repositories;

import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import io.github.rkaushikdev.jeepdolly.domain.CommodityBook;
import io.github.rkaushikdev.jeepdolly.persistence.models.CommodityRow;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;

/** The commodity table, read whole because it is small and read often. */
public final class CommodityRepository {

    private final EntityManager entityManager;

    public CommodityRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** The entity manager this repository asks its questions of. */
    public EntityManager entityManager() {
        return entityManager;
    }

    /** Every line on file, retired ones included, as the book the rules read. */
    public CommodityBook book() {
        return read(null);
    }

    /** Every line still hauled on a day, as the book the rules read. */
    public CommodityBook book(LocalDate on) {
        return read(on);
    }

    private CommodityBook read(LocalDate on) {
        CommodityBook book = new CommodityBook();
        for (CommodityRow row : entityManager
                .createQuery("select c from CommodityRow c order by c.code", CommodityRow.class)
                .getResultList()) {
            boolean gone = on != null
                    && row.getRetired().filter(day -> !day.isAfter(on)).isPresent();
            if (!gone) {
                book.add(row.toDomain());
            }
        }
        return book;
    }

    /**
     * Stop hauling a commodity from a day. The row stays, for the permits that already name it.
     *
     * @throws NotOnFile when nobody filed that code
     */
    public CommodityRow retire(String code, LocalDate on) {
        CommodityRow row = entityManager.find(CommodityRow.class, code);
        if (row == null) {
            throw new NotOnFile("no commodity " + code, Facts.of("code", code));
        }
        row.setRetired(on);
        return row;
    }
}
