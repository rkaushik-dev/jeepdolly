package io.github.rkaushikdev.jeepdolly.persistence.models;

import static io.github.rkaushikdev.jeepdolly.persistence.Columns.CODE_LENGTH;
import static io.github.rkaushikdev.jeepdolly.persistence.Columns.NAME_LENGTH;
import static io.github.rkaushikdev.jeepdolly.persistence.Columns.TEXT_LENGTH;

import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.utilities.Crossing;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Optional;

/**
 * One wire over one segment, as last measured.
 *
 * <p>A crossing is edited, which is the exception among the registers: a wire is a fact about the
 * world today rather than a document, and when the line crew raises a span permanently the height
 * on the card simply changes. The measured day is on the row because a crossing measured in 1998
 * is not evidence about a wire in 2025. An empty lift height means the wire cannot be lifted at all,
 * which is a real distinction and not a missing value.
 */
@Entity
@Table(name = "line_crossings")
public class LineCrossingRow {

    @Id
    @Column(name = "ident", length = CODE_LENGTH, nullable = false)
    private String ident;

    @Column(name = "segment_ident", length = CODE_LENGTH, nullable = false)
    private String segmentIdent;

    @Column(name = "place", length = NAME_LENGTH, nullable = false)
    private String place;

    @Column(name = "height")
    private Inches height;

    @Column(name = "owner_ident", length = CODE_LENGTH, nullable = false)
    private String ownerIdent;

    @Column(name = "lift_to")
    private Inches liftTo;

    @Column(name = "measured")
    private LocalDate measured;

    @Column(name = "note", length = TEXT_LENGTH, nullable = false)
    private String note = "";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_ident", insertable = false, updatable = false)
    private LineOwnerRow owner;

    protected LineCrossingRow() {}

    /** The row for a crossing, with no day it was measured written down. */
    public static LineCrossingRow of(Crossing crossing) {
        return of(crossing, null);
    }

    /**
     * The row for a crossing as a survey measured it.
     *
     * @param measured the day of the survey, or null when nobody wrote it down
     */
    public static LineCrossingRow of(Crossing crossing, LocalDate measured) {
        LineCrossingRow row = new LineCrossingRow();
        row.ident = crossing.ident();
        row.segmentIdent = crossing.segment();
        row.place = crossing.place();
        row.height = crossing.height();
        row.ownerIdent = crossing.owner();
        row.liftTo = crossing.liftTo().orElse(null);
        row.measured = measured;
        row.note = crossing.note();
        return row;
    }

    public String getIdent() {
        return ident;
    }

    public String getSegmentIdent() {
        return segmentIdent;
    }

    public String getPlace() {
        return place;
    }

    public Inches getHeight() {
        return height;
    }

    public void setHeight(Inches height) {
        this.height = height;
    }

    /** What it hangs at, which is what an enforcement officer asks. */
    public Inches getClearance() {
        return height;
    }

    public String getOwnerIdent() {
        return ownerIdent;
    }

    /** What the owner can raise it to, or empty when it cannot be lifted. */
    public Optional<Inches> getLiftTo() {
        return Optional.ofNullable(liftTo);
    }

    /** What the owner can raise it to, or null when it cannot be lifted. */
    public void setLiftTo(Inches liftTo) {
        this.liftTo = liftTo;
    }

    public Optional<LocalDate> getMeasured() {
        return Optional.ofNullable(measured);
    }

    public void setMeasured(LocalDate measured) {
        this.measured = measured;
    }

    public String getNote() {
        return note;
    }

    public Crossing toDomain() {
        return new Crossing(ident, segmentIdent, place, height, ownerIdent, Optional.ofNullable(liftTo), note);
    }

    @Override
    public String toString() {
        return "<LineCrossingRow " + ident + ">";
    }
}
