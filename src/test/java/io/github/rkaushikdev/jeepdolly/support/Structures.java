package io.github.rkaushikdev.jeepdolly.support;

import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.structures.Structure;
import io.github.rkaushikdev.jeepdolly.structures.StructureBook;
import io.github.rkaushikdev.jeepdolly.structures.StructureKind;
import java.time.LocalDate;
import java.util.List;

/** The six structures on the test network, two of them worth worrying about. */
public final class Structures {

    /**
     * One row of the test inventory. A zero rating, posting or clearance means the record has none.
     *
     * @param clearance the height under it, in inches
     */
    public record Row(
            String ident,
            String name,
            String segment,
            String kind,
            List<Integer> spans,
            int built,
            int operating,
            int inventory,
            int posted,
            int clearance) {}

    /** Ident, name, segment, kind, spans, built, operating, inventory, posted, clearance. */
    public static final List<Row> STRUCTURES = List.of(
            new Row("1247", "Kennebec River", "US201-2", "bridge", List.of(120, 120, 120), 1937, 132000, 88000, 0, 0),
            new Row("1533", "Carrabassett Stream", "SA148-1", "bridge", List.of(48), 1954, 60000, 42000, 48000, 0),
            new Row("2201", "Norridgewock overpass", "US2-1", "overpass", List.of(), 1972, 0, 0, 0, 165),
            new Row("3310", "Martin Stream", "SR43-1", "culvert", List.of(14), 1968, 90000, 70000, 0, 0),
            new Row("4102", "Pittsfield gantry", "I95-2", "sign gantry", List.of(), 1998, 0, 0, 0, 198),
            new Row("5006", "Sandy River", "SR8-1", "bridge", List.of(90, 90), 1961, 110000, 76000, 0, 0));

    /** Every structure was last inspected on this day. */
    public static final LocalDate INSPECTED = LocalDate.of(2024, 6, 1);

    private Structures() {}

    /** The six structures of the test network, inspected on 2024-06-01. */
    public static StructureBook structureBook() {
        StructureBook book = new StructureBook();
        for (Row row : STRUCTURES) {
            Structure.Builder built = Structure.builder(
                            row.ident(), row.name(), row.segment(), StructureKind.of(row.kind()))
                    .spansFeet(row.spans())
                    .built(row.built())
                    .inspected(INSPECTED);
            if (row.operating() != 0) {
                built.operatingRating(new Pounds(row.operating()));
            }
            if (row.inventory() != 0) {
                built.inventoryRating(new Pounds(row.inventory()));
            }
            if (row.posted() != 0) {
                built.posted(new Pounds(row.posted()));
            }
            if (row.clearance() != 0) {
                built.clearance(new Inches(row.clearance()));
            }
            book.add(built.build());
        }
        return book;
    }
}
