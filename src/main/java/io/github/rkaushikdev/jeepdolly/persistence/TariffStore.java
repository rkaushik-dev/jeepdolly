package io.github.rkaushikdev.jeepdolly.persistence;

import io.github.rkaushikdev.jeepdolly.Conflict;
import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.domain.HighwayClass;
import io.github.rkaushikdev.jeepdolly.fees.FeeSchedule;
import io.github.rkaushikdev.jeepdolly.fees.ScheduleBook;
import io.github.rkaushikdev.jeepdolly.fees.WeightStep;
import io.github.rkaushikdev.jeepdolly.limits.PermitKind;
import io.github.rkaushikdev.jeepdolly.limits.SizeBook;
import io.github.rkaushikdev.jeepdolly.limits.SizeTable;
import io.github.rkaushikdev.jeepdolly.limits.StatutoryBook;
import io.github.rkaushikdev.jeepdolly.limits.StatutoryTable;
import io.github.rkaushikdev.jeepdolly.persistence.models.BaseFeeRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.MileFeeRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.ScheduleRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.SizeRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.StatutoryRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.WeightStepRow;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reading the published tables off the database, and seeding an empty one.
 *
 * <p>Three books come out of here and they are the same three types the rules already read, so
 * nothing downstream knows the tables came from rows rather than from code. Seeding writes the
 * tables the code carries, dated from a day the caller names, and refuses a database that already
 * has tables, because seeding twice would put two sets of numbers on one effective date and nobody
 * could say which was in force.
 */
public final class TariffStore {

    /** The day the coded tables are seeded from when nobody names one. */
    public static final LocalDate FIRST = LocalDate.of(2024, 1, 1);

    private TariffStore() {}

    /** Whether anybody has filed a table yet. */
    public static boolean seeded(EntityManager entityManager) {
        return !entityManager
                .createQuery("select s from StatutoryRow s", StatutoryRow.class)
                .setMaxResults(1)
                .getResultList()
                .isEmpty();
    }

    /** Write the tables the code carries, dated from {@link #FIRST}. */
    public static int seed(EntityManager entityManager) {
        return seed(entityManager, FIRST);
    }

    /**
     * Write the tables the code carries, dated from a day.
     *
     * @return how many tables were written: one per class of road for the weights and the sizes,
     *     and one schedule
     * @throws Conflict when the tables are already on file
     */
    public static int seed(EntityManager entityManager, LocalDate effective) {
        if (seeded(entityManager)) {
            throw new Conflict("the tariff tables are already on file");
        }
        int written = 0;
        StatutoryBook statutes = StatutoryBook.standard(effective);
        for (HighwayClass highwayClass : HighwayClass.values()) {
            StatutoryTable table = statutes.on(highwayClass, effective);
            entityManager.persist(new StatutoryRow(
                    highwayClass,
                    effective,
                    table.groupLimit(1),
                    table.groupLimit(2),
                    table.groupLimit(3),
                    table.groupLimit(4),
                    table.beyondTable(),
                    table.gross(),
                    table.tireRate()));
            written++;
        }
        SizeBook sizes = SizeBook.standard();
        for (HighwayClass highwayClass : HighwayClass.values()) {
            entityManager.persist(SizeRow.of(sizes.on(highwayClass), effective));
            written++;
        }
        FeeSchedule schedule = FeeSchedule.standard(effective);
        entityManager.persist(new ScheduleRow(
                effective, schedule.review(), schedule.amendment(), schedule.minimum(), schedule.annual()));
        schedule.base()
                .forEach((kind, amount) -> entityManager.persist(new BaseFeeRow(effective, kind.value(), amount)));
        schedule.perMile()
                .forEach((highwayClass, amount) ->
                        entityManager.persist(new MileFeeRow(effective, highwayClass, amount)));
        for (int position = 0; position < schedule.steps().size(); position++) {
            WeightStep step = schedule.steps().get(position);
            entityManager.persist(
                    new WeightStepRow(effective, position, step.upTo().orElse(null), step.perBlock()));
        }
        return written + 1;
    }

    /** Every statutory table on file, as the book the rules read. */
    public static StatutoryBook statutoryBook(EntityManager entityManager) {
        StatutoryBook book = new StatutoryBook();
        for (StatutoryRow row : entityManager
                .createQuery("select s from StatutoryRow s order by s.effective", StatutoryRow.class)
                .getResultList()) {
            Map<Integer, Pounds> byCount = new LinkedHashMap<>();
            byCount.put(1, row.getSingle());
            byCount.put(2, row.getTandem());
            byCount.put(3, row.getTridem());
            byCount.put(4, row.getQuad());
            book.file(new StatutoryTable(
                    row.getHighwayClass(),
                    row.getEffective(),
                    byCount,
                    row.getBeyond(),
                    row.getGross(),
                    row.getTireRate()));
        }
        return book;
    }

    /** The size tables in force on a day. */
    public static SizeBook sizeBook(EntityManager entityManager, LocalDate on) {
        SizeBook book = new SizeBook();
        for (SizeRow row : entityManager
                .createQuery("select s from SizeRow s where s.effective <= :on order by s.effective", SizeRow.class)
                .setParameter("on", on)
                .getResultList()) {
            book.file(new SizeTable(row.getHighwayClass(), row.getLegal(), row.getRoutine()));
        }
        return book;
    }

    /** Every fee schedule on file, as the book the pricing reads. */
    public static ScheduleBook scheduleBook(EntityManager entityManager) {
        ScheduleBook book = new ScheduleBook();
        for (ScheduleRow row : entityManager
                .createQuery("select s from ScheduleRow s order by s.effective", ScheduleRow.class)
                .getResultList()) {
            book.file(new FeeSchedule(
                    row.getEffective(),
                    baseFees(entityManager, row.getEffective()),
                    mileFees(entityManager, row.getEffective()),
                    steps(entityManager, row.getEffective()),
                    row.getReview(),
                    row.getAmendment(),
                    row.getMinimum(),
                    row.getAnnual()));
        }
        return book;
    }

    /** The base fees of one schedule, in the order the kinds of permit are declared. */
    private static Map<PermitKind, Dollars> baseFees(EntityManager entityManager, LocalDate effective) {
        Map<PermitKind, Dollars> found = new LinkedHashMap<>();
        entityManager
                .createQuery("select b from BaseFeeRow b where b.effective = :effective", BaseFeeRow.class)
                .setParameter("effective", effective)
                .getResultList()
                .stream()
                .sorted(Comparator.comparing(one -> PermitKind.of(one.getKind())))
                .forEach(one -> found.put(PermitKind.of(one.getKind()), one.getAmount()));
        return found;
    }

    /** The mile rates of one schedule, in the order the classes of road are declared. */
    private static Map<HighwayClass, Dollars> mileFees(EntityManager entityManager, LocalDate effective) {
        Map<HighwayClass, Dollars> found = new LinkedHashMap<>();
        entityManager
                .createQuery("select m from MileFeeRow m where m.effective = :effective", MileFeeRow.class)
                .setParameter("effective", effective)
                .getResultList()
                .stream()
                .sorted(Comparator.comparing(MileFeeRow::getHighwayClass))
                .forEach(one -> found.put(one.getHighwayClass(), one.getAmount()));
        return found;
    }

    private static List<WeightStep> steps(EntityManager entityManager, LocalDate effective) {
        return entityManager
                .createQuery(
                        "select w from WeightStepRow w where w.effective = :effective order by w.position",
                        WeightStepRow.class)
                .setParameter("effective", effective)
                .getResultList()
                .stream()
                .map(one -> new WeightStep(one.getUpTo(), one.getPerBlock()))
                .toList();
    }
}
