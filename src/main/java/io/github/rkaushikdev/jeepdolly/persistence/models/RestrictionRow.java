package io.github.rkaushikdev.jeepdolly.persistence.models;

import static io.github.rkaushikdev.jeepdolly.persistence.Columns.CODE_LENGTH;
import static io.github.rkaushikdev.jeepdolly.persistence.Columns.NAME_LENGTH;
import static io.github.rkaushikdev.jeepdolly.persistence.Columns.TEXT_LENGTH;

import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.domain.RestrictionKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import org.hibernate.annotations.FractionalSeconds;

/**
 * One thing wrong with one segment, for a while or for good.
 *
 * <p>A restriction is deleted rather than retired, which is the opposite of the reference tables
 * and it is deliberate. A lifted restriction is not history anybody needs: the order that lifted it
 * is on the audit trail, and a table full of dead rows makes every route check slower. A curfew is
 * stored as the weekday digits it runs on, Monday as {@code 0}, and two clock times.
 */
@Entity
@Table(
        name = "restrictions",
        indexes = {
            @Index(name = "ix_restrictions_kind", columnList = "kind"),
            @Index(name = "ix_restrictions_segment_ident", columnList = "segment_ident")
        })
public class RestrictionRow {

    @Id
    @Column(name = "ident", length = CODE_LENGTH, nullable = false)
    private String ident;

    @Column(name = "segment_ident", length = CODE_LENGTH, nullable = false)
    private String segmentIdent;

    @Column(name = "kind", length = 32, nullable = false)
    private RestrictionKind kind;

    @Column(name = "note", length = TEXT_LENGTH, nullable = false)
    private String note;

    @Column(name = "height")
    private Inches height;

    @Column(name = "width")
    private Inches width;

    @Column(name = "weight")
    private Pounds weight;

    @Column(name = "region", length = NAME_LENGTH)
    private String region;

    @Column(name = "curfew_days", length = 16)
    private String curfewDays;

    @FractionalSeconds(0)
    @Column(name = "curfew_from")
    private LocalTime curfewFrom;

    @FractionalSeconds(0)
    @Column(name = "curfew_to")
    private LocalTime curfewTo;

    @Column(name = "effective")
    private LocalDate effective;

    @Column(name = "expires")
    private LocalDate expires;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "segment_ident",
            nullable = false,
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_restrictions_segment_ident_segments"))
    private SegmentRow segment;

    protected RestrictionRow() {}

    /** A restriction with nothing to compare against yet; set what its kind carries. */
    public RestrictionRow(String ident, String segmentIdent, RestrictionKind kind, String note) {
        this.ident = ident;
        this.segmentIdent = segmentIdent;
        this.kind = kind;
        this.note = note;
    }

    public String getIdent() {
        return ident;
    }

    public String getSegmentIdent() {
        return segmentIdent;
    }

    public RestrictionKind getKind() {
        return kind;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Optional<Inches> getHeight() {
        return Optional.ofNullable(height);
    }

    /** The clearance it allows, or null for none. */
    public void setHeight(Inches height) {
        this.height = height;
    }

    public Optional<Inches> getWidth() {
        return Optional.ofNullable(width);
    }

    /** The width it allows, or null for none. */
    public void setWidth(Inches width) {
        this.width = width;
    }

    public Optional<Pounds> getWeight() {
        return Optional.ofNullable(weight);
    }

    /** The weight it allows, or null for none. */
    public void setWeight(Pounds weight) {
        this.weight = weight;
    }

    public Optional<String> getRegion() {
        return Optional.ofNullable(region);
    }

    /** The region a thaw order names, or null for none. */
    public void setRegion(String region) {
        this.region = region;
    }

    /** The weekdays a curfew runs on, as digits with Monday as {@code 0}. */
    public Optional<String> getCurfewDays() {
        return Optional.ofNullable(curfewDays);
    }

    public void setCurfewDays(String curfewDays) {
        this.curfewDays = curfewDays;
    }

    public Optional<LocalTime> getCurfewFrom() {
        return Optional.ofNullable(curfewFrom);
    }

    public void setCurfewFrom(LocalTime curfewFrom) {
        this.curfewFrom = curfewFrom;
    }

    public Optional<LocalTime> getCurfewTo() {
        return Optional.ofNullable(curfewTo);
    }

    public void setCurfewTo(LocalTime curfewTo) {
        this.curfewTo = curfewTo;
    }

    public Optional<LocalDate> getEffective() {
        return Optional.ofNullable(effective);
    }

    /** The first day it applies, or null for always. */
    public void setEffective(LocalDate effective) {
        this.effective = effective;
    }

    public Optional<LocalDate> getExpires() {
        return Optional.ofNullable(expires);
    }

    /** The last day it applies, or null for never lifting. */
    public void setExpires(LocalDate expires) {
        this.expires = expires;
    }

    /** The segment row it hangs off, once the row has been read back from the database. */
    public Optional<SegmentRow> getSegment() {
        return Optional.ofNullable(segment);
    }

    @Override
    public String toString() {
        return "<RestrictionRow " + ident + " " + kind + ">";
    }
}
