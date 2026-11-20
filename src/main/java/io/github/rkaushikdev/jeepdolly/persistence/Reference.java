package io.github.rkaushikdev.jeepdolly.persistence;

import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.Logs;
import io.github.rkaushikdev.jeepdolly.conditions.ConditionLibrary;
import io.github.rkaushikdev.jeepdolly.domain.CommodityBook;
import io.github.rkaushikdev.jeepdolly.domain.Network;
import io.github.rkaushikdev.jeepdolly.fees.ScheduleBook;
import io.github.rkaushikdev.jeepdolly.limits.SizeBook;
import io.github.rkaushikdev.jeepdolly.limits.StatutoryBook;
import io.github.rkaushikdev.jeepdolly.persistence.models.BaseFeeRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.CommodityRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.ConditionTextRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.LineCrossingRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.LineOwnerRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.MileFeeRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.NodeRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.ScheduleRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.SegmentRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.SizeRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.StatutoryRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.StructureRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.WeightStepRow;
import io.github.rkaushikdev.jeepdolly.persistence.repositories.CommodityRepository;
import io.github.rkaushikdev.jeepdolly.structures.StructureBook;
import io.github.rkaushikdev.jeepdolly.utilities.LineBook;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.hibernate.Hibernate;
import org.hibernate.engine.spi.EntityEntry;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.engine.spi.Status;

/**
 * The published books as they stood on one day: the slow reading a cache holds between requests.
 *
 * <p>Which books are in and which are out is the whole of the design. In are the road log, the
 * structure inventory, the commodity table, the size tables, the statutory tables, the fee
 * schedules, the condition library and the overhead lines: every one published, changing on a
 * schedule, read whole to answer one question. Out are the restrictions, the carriers, the seasonal
 * orders and the enforcement accounts, which change during the day; a permit written against
 * yesterday's closure is the failure this office cannot have, so those are read fresh every time.
 */
public record Reference(
        LocalDate day,
        Network network,
        StructureBook structures,
        CommodityBook commodities,
        SizeBook sizes,
        StatutoryBook statutes,
        ScheduleBook schedules,
        ConditionLibrary library,
        LineBook lines) {

    /** Writing any of these empties the cache. Reading none of them leaves it. */
    public static final List<Class<?>> WATCHED = List.of(
            NodeRow.class,
            SegmentRow.class,
            StructureRow.class,
            CommodityRow.class,
            ConditionTextRow.class,
            LineCrossingRow.class,
            LineOwnerRow.class,
            BaseFeeRow.class,
            MileFeeRow.class,
            ScheduleRow.class,
            SizeRow.class,
            StatutoryRow.class,
            WeightStepRow.class);

    /**
     * Build every published book off the rows, which is the slow path.
     *
     * <p>The unseeded fallbacks live with the reading, so a command line report on a fresh database
     * behaves exactly as a request does.
     */
    public static Reference read(EntityManager entityManager, LocalDate day) {
        return new Reference(
                day,
                Books.networkFrom(entityManager),
                Books.structuresFrom(entityManager),
                new CommodityRepository(entityManager).book(day),
                sizesFor(entityManager, day),
                statutesFor(entityManager),
                schedulesFor(entityManager),
                ConditionLibrary.standard(),
                Books.linesFrom(entityManager));
    }

    /**
     * The statutory tables off the rows, or the coded ones on a bare database.
     *
     * <p>The fallback is not a convenience. A database that has not been seeded is one somebody is
     * about to seed, and refusing every request until they do would mean the office cannot answer
     * a telephone call during an install. It says so on the log, once per read.
     */
    public static StatutoryBook statutesFor(EntityManager entityManager) {
        StatutoryBook book = TariffStore.statutoryBook(entityManager);
        if (book.size() > 0) {
            return book;
        }
        Logs.trouble("tariff.unseeded", Facts.of("table", "statutory_limits"));
        return StatutoryBook.standard();
    }

    /** The size tables off the rows, or the coded ones on a bare database. */
    public static SizeBook sizesFor(EntityManager entityManager, LocalDate on) {
        SizeBook book = TariffStore.sizeBook(entityManager, on);
        if (book.size() > 0) {
            return book;
        }
        Logs.trouble("tariff.unseeded", Facts.of("table", "size_limits"));
        return SizeBook.standard();
    }

    /** The fee schedules off the rows, or the coded ones on a bare database. */
    public static ScheduleBook schedulesFor(EntityManager entityManager) {
        ScheduleBook book = TariffStore.scheduleBook(entityManager);
        if (book.size() > 0) {
            return book;
        }
        Logs.trouble("tariff.unseeded", Facts.of("table", "fee_schedules"));
        return ScheduleBook.standard();
    }

    /**
     * Whether the next flush is about to change a published table.
     *
     * <p>New rows count, deletes count and so do edits. An application row moving to submitted is
     * not a published change and this says so, which is the whole reason the watched list is a list
     * rather than everything.
     */
    public static boolean touched(EntityManager entityManager) {
        SessionImplementor session = entityManager.unwrap(SessionImplementor.class);
        for (Map.Entry<Object, EntityEntry> one :
                session.getPersistenceContextInternal().reentrantSafeEntityEntries()) {
            Object row = one.getKey();
            if (!WATCHED.contains(Hibernate.getClass(row))) {
                continue;
            }
            EntityEntry entry = one.getValue();
            if (entry.getStatus() == Status.DELETED || !entry.isExistsInDatabase()) {
                return true;
            }
            if (entry.getStatus() == Status.MANAGED && entry.getLoadedState() != null) {
                Object[] current = entry.getPersister().getValues(row);
                int[] dirty = entry.getPersister().findDirty(current, entry.getLoadedState(), row, session);
                if (dirty != null && dirty.length > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Empty the cache after any successful flush of {@code database} that wrote a published table.
     *
     * <p>The hook fires after the flush rather than before it, so a flush that fails on a
     * constraint leaves the cache alone. It fires on the flush and not on the commit, because a
     * route reads the office again inside the same transaction, and waiting for the commit would
     * serve it stale books it wrote itself.
     *
     * @return the listener, for {@link Database#stopListening}
     */
    public static FlushListener watch(Database database, ReferenceCache cache) {
        return database.listen(changed -> {
            if (changed.stream().anyMatch(WATCHED::contains)) {
                cache.clear();
            }
        });
    }
}
