package io.github.rkaushikdev.jeepdolly.limits;

import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.domain.HighwayClass;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Every statutory table on file, asked for by class of road and by date.
 *
 * <p>The tables are data and they are versioned by the date they took effect, because a permit
 * issued in March is judged by the March table even if it is argued about in September.
 *
 * <p>The interstate table is the federal one and the state cannot raise it. The others are the
 * state's own and they are higher, which is the usual surprise: a six axle combination that is legal
 * on US 2 becomes overweight the moment it turns onto I-95.
 */
public final class StatutoryBook {

    /** The day the tables the code carries take effect, unless told otherwise. */
    public static final LocalDate STANDARD_EFFECTIVE = LocalDate.of(2024, 1, 1);

    private final List<StatutoryTable> tables = new ArrayList<>();

    /** The tables as they stand, taking effect on the first of January 2024. */
    public static StatutoryBook standard() {
        return standard(STANDARD_EFFECTIVE);
    }

    /** The tables as they stand, which is what the counter reads today, dated from a day. */
    public static StatutoryBook standard(LocalDate effective) {
        StatutoryBook book = new StatutoryBook();
        book.file(table(HighwayClass.INTERSTATE, 20000, 34000, 42500, 50000, 6000, 80000, 500, effective));
        book.file(table(HighwayClass.ARTERIAL, 22400, 41000, 48000, 56000, 7000, 100000, 600, effective));
        book.file(table(HighwayClass.STATE_AID, 22400, 38000, 44000, 50000, 6000, 94000, 600, effective));
        book.file(table(HighwayClass.TOWN_WAY, 20000, 34000, 42000, 48000, 6000, 80000, 500, effective));
        return book;
    }

    private static StatutoryTable table(
            HighwayClass highwayClass,
            int single,
            int tandem,
            int tridem,
            int quad,
            int beyond,
            int gross,
            int tireRate,
            LocalDate effective) {
        return new StatutoryTable(
                highwayClass,
                effective,
                Map.of(1, new Pounds(single), 2, new Pounds(tandem), 3, new Pounds(tridem), 4, new Pounds(quad)),
                new Pounds(beyond),
                new Pounds(gross),
                tireRate);
    }

    /**
     * Put a table on file.
     *
     * @throws Impossible when a table for the same class already takes effect that day
     */
    public StatutoryTable file(StatutoryTable table) {
        for (StatutoryTable existing : tables) {
            if (existing.highwayClass() == table.highwayClass()
                    && existing.effective().equals(table.effective())) {
                throw new Impossible("a " + table.highwayClass() + " table already takes effect that day");
            }
        }
        tables.add(table);
        return table;
    }

    /**
     * The table in force for a class of road on a day: the latest one that had taken effect.
     *
     * @throws NotOnFile when no table for that class had taken effect by then
     */
    public StatutoryTable on(HighwayClass highwayClass, LocalDate day) {
        return tables.stream()
                .filter(table -> table.highwayClass() == highwayClass
                        && !table.effective().isAfter(day))
                .max(Comparator.comparing(StatutoryTable::effective))
                .orElseThrow(() -> new NotOnFile(
                        "no " + highwayClass + " table in force on " + day,
                        Facts.of("highway_class", highwayClass.toString())));
    }

    /** Every class of road with a table on file, sorted by name. */
    public List<HighwayClass> classes() {
        return tables.stream()
                .map(StatutoryTable::highwayClass)
                .distinct()
                .sorted(Comparator.comparing(HighwayClass::toString))
                .toList();
    }

    /** How many tables are on file. */
    public int size() {
        return tables.size();
    }
}
