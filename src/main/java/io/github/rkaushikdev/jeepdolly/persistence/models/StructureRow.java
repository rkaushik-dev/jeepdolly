package io.github.rkaushikdev.jeepdolly.persistence.models;

import static io.github.rkaushikdev.jeepdolly.persistence.Columns.CODE_LENGTH;
import static io.github.rkaushikdev.jeepdolly.persistence.Columns.NAME_LENGTH;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.Written;
import io.github.rkaushikdev.jeepdolly.structures.Structure;
import io.github.rkaushikdev.jeepdolly.structures.StructureKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * One structure, as the last inspection left it.
 *
 * <p>The row is edited in place: when an inspection produces a new operating rating the row is
 * updated, because the current rating is what every route check needs, and the inspection date on
 * the row says how old the number is. The spans are one comma separated column rather than a child
 * table, because nothing ever asks about one span: they are read together, always, by the loading
 * calculation.
 */
@Entity
@Table(name = "structures")
public class StructureRow {

    @Id
    @Column(name = "ident", length = CODE_LENGTH, nullable = false)
    private String ident;

    @Column(name = "name", length = NAME_LENGTH, nullable = false)
    private String name;

    @Column(name = "segment_ident", length = CODE_LENGTH, nullable = false)
    private String segmentIdent;

    @Column(name = "kind", length = 32, nullable = false)
    private StructureKind kind;

    @Column(name = "spans", length = NAME_LENGTH, nullable = false)
    private String spans = "";

    @Column(name = "built", nullable = false)
    private int built;

    @Column(name = "inspected")
    private LocalDate inspected;

    @Column(name = "inventory_rating")
    private Pounds inventoryRating;

    @Column(name = "operating_rating")
    private Pounds operatingRating;

    @Column(name = "posted")
    private Pounds posted;

    @Column(name = "clearance")
    private Inches clearance;

    @Column(name = "deck_width")
    private Inches deckWidth;

    protected StructureRow() {}

    /** A structure with no spans, no year and no ratings written down yet. */
    public StructureRow(String ident, String name, String segmentIdent, StructureKind kind) {
        this.ident = ident;
        this.name = name;
        this.segmentIdent = segmentIdent;
        this.kind = kind;
    }

    /** The spans as the one column holds them: {@code 120,120,120}. */
    public static String spansToText(List<Integer> spans) {
        return spans.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    /**
     * The column back into spans in whole feet.
     *
     * @throws Impossible when the column holds something that is not a list of numbers
     */
    public static List<Integer> spansFromText(String text) {
        if (text.isEmpty()) {
            return List.of();
        }
        List<Integer> found = new ArrayList<>();
        try {
            for (String one : text.split(",", -1)) {
                found.add(Integer.parseInt(one.strip()));
            }
        } catch (NumberFormatException unreadable) {
            throw new Impossible("cannot read " + Written.quoted(text) + " as a list of spans");
        }
        return List.copyOf(found);
    }

    /** The row for a structure on the inventory. */
    public static StructureRow of(Structure structure) {
        StructureRow row = new StructureRow(structure.ident(), structure.name(), structure.segment(), structure.kind());
        row.spans = spansToText(structure.spansFeet());
        row.built = structure.built();
        row.inspected = structure.inspected().orElse(null);
        row.inventoryRating = structure.inventoryRating().orElse(null);
        row.operatingRating = structure.operatingRating().orElse(null);
        row.posted = structure.posted().orElse(null);
        row.clearance = structure.clearance().orElse(null);
        row.deckWidth = structure.deckWidth().orElse(null);
        return row;
    }

    public String getIdent() {
        return ident;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSegmentIdent() {
        return segmentIdent;
    }

    public StructureKind getKind() {
        return kind;
    }

    public String getSpans() {
        return spans;
    }

    public void setSpans(String spans) {
        this.spans = spans;
    }

    public int getBuilt() {
        return built;
    }

    public void setBuilt(int built) {
        this.built = built;
    }

    public Optional<LocalDate> getInspected() {
        return Optional.ofNullable(inspected);
    }

    public void setInspected(LocalDate inspected) {
        this.inspected = inspected;
    }

    public Optional<Pounds> getInventoryRating() {
        return Optional.ofNullable(inventoryRating);
    }

    /** The daily rating, or null for none. */
    public void setInventoryRating(Pounds inventoryRating) {
        this.inventoryRating = inventoryRating;
    }

    public Optional<Pounds> getOperatingRating() {
        return Optional.ofNullable(operatingRating);
    }

    /** The permit rating, or null for none. */
    public void setOperatingRating(Pounds operatingRating) {
        this.operatingRating = operatingRating;
    }

    public Optional<Pounds> getPosted() {
        return Optional.ofNullable(posted);
    }

    /** The weight on the sign, or null when it is not posted. */
    public void setPosted(Pounds posted) {
        this.posted = posted;
    }

    public Optional<Inches> getClearance() {
        return Optional.ofNullable(clearance);
    }

    public void setClearance(Inches clearance) {
        this.clearance = clearance;
    }

    public Optional<Inches> getDeckWidth() {
        return Optional.ofNullable(deckWidth);
    }

    public void setDeckWidth(Inches deckWidth) {
        this.deckWidth = deckWidth;
    }

    public Structure toDomain() {
        return new Structure(
                ident,
                name,
                segmentIdent,
                kind,
                spansFromText(spans),
                built,
                Optional.ofNullable(inspected),
                Optional.ofNullable(inventoryRating),
                Optional.ofNullable(operatingRating),
                Optional.ofNullable(posted),
                Optional.ofNullable(clearance),
                Optional.ofNullable(deckWidth));
    }

    @Override
    public String toString() {
        return "<StructureRow " + ident + ">";
    }
}
