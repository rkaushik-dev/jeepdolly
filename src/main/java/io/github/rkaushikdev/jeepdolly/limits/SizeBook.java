package io.github.rkaushikdev.jeepdolly.limits;

import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import io.github.rkaushikdev.jeepdolly.domain.HighwayClass;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** One size table for each class of road. */
public final class SizeBook {

    private final Map<HighwayClass, SizeTable> tables = new LinkedHashMap<>();

    /** The size tables as they stand. */
    public static SizeBook standard() {
        SizeBook book = new SizeBook();
        book.file(new SizeTable(
                HighwayClass.INTERSTATE,
                Envelope.read("8'6", "13'6", "75'", "3'", "4'"),
                Envelope.read("16'", "16'", "120'", "10'", "15'")));
        book.file(new SizeTable(
                HighwayClass.ARTERIAL,
                Envelope.read("8'6", "13'6", "75'", "3'", "4'"),
                Envelope.read("16'", "15'6", "120'", "10'", "15'")));
        book.file(new SizeTable(
                HighwayClass.STATE_AID,
                Envelope.read("8'6", "13'6", "68'", "3'", "4'"),
                Envelope.read("14'", "14'6", "100'", "8'", "12'")));
        book.file(new SizeTable(
                HighwayClass.TOWN_WAY,
                Envelope.read("8'6", "13'6", "68'", "3'", "4'"),
                Envelope.read("12'", "14'", "80'", "6'", "10'")));
        return book;
    }

    /** Put a table on file, replacing whatever that class of road had before. */
    public SizeTable file(SizeTable table) {
        tables.put(table.highwayClass(), table);
        return table;
    }

    /** @throws NotOnFile when there is no size table for that class of road */
    public SizeTable on(HighwayClass highwayClass) {
        SizeTable found = tables.get(highwayClass);
        if (found == null) {
            throw new NotOnFile(
                    "no size table for " + highwayClass, Facts.of("highway_class", highwayClass.toString()));
        }
        return found;
    }

    /**
     * The table a trip is held to when it crosses several classes of road.
     *
     * <p>A trip is held to the narrowest legal box it touches, which is not always the same table as
     * the lowest one, so this picks per measure and hands back a table with the tightest of each. It
     * is named for the class with the narrowest legal width, the first of them on a tie.
     *
     * @throws Impossible for a trip over no roads at all
     * @throws NotOnFile when one of the classes has no table
     */
    public SizeTable tightest(List<HighwayClass> classes) {
        if (classes.isEmpty()) {
            throw new Impossible("a trip runs over at least one class of road");
        }
        List<SizeTable> found = classes.stream().map(this::on).toList();
        SizeTable narrowest = Collections.min(
                found, Comparator.comparingInt(table -> table.legal().width().value()));
        return new SizeTable(
                narrowest.highwayClass(),
                tightestBox(found.stream().map(SizeTable::legal).toList()),
                tightestBox(found.stream().map(SizeTable::routine).toList()));
    }

    private static Envelope tightestBox(List<Envelope> boxes) {
        return new Envelope(
                least(boxes, Envelope::width),
                least(boxes, Envelope::height),
                least(boxes, Envelope::length),
                least(boxes, Envelope::frontOverhang),
                least(boxes, Envelope::rearOverhang));
    }

    private static Inches least(List<Envelope> boxes, Function<Envelope, Inches> measure) {
        return boxes.stream().map(measure).min(Comparator.naturalOrder()).orElseThrow();
    }

    /** How many classes of road have a table. */
    public int size() {
        return tables.size();
    }
}
