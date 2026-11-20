package io.github.rkaushikdev.jeepdolly.persistence.models;

import static io.github.rkaushikdev.jeepdolly.persistence.Columns.CODE_LENGTH;
import static io.github.rkaushikdev.jeepdolly.persistence.Columns.NAME_LENGTH;
import static io.github.rkaushikdev.jeepdolly.persistence.Columns.TEXT_LENGTH;

import io.github.rkaushikdev.jeepdolly.permits.Reason;
import io.github.rkaushikdev.jeepdolly.permits.Reprint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * One duplicate copy of a permit, as it was issued.
 *
 * <p>The register is a list beside each permit, keyed on the copy number printed on the paper, so a
 * copy number is handed out once and the yearly count of copies is a count of rows.
 */
@Entity
@Table(
        name = "reprints",
        indexes = {
            @Index(name = "ix_reprints_issued_on", columnList = "issued_on"),
            @Index(name = "ix_reprints_reason", columnList = "reason")
        })
@IdClass(ReprintRow.Key.class)
public class ReprintRow {

    /** A permit's number and the copy number printed on the paper. */
    public record Key(String permit, int copy) {}

    @Id
    @Column(name = "permit", length = CODE_LENGTH, nullable = false)
    private String permit;

    @Id
    @Column(name = "copy", nullable = false)
    private int copy;

    @Column(name = "issued_on", nullable = false)
    private LocalDate issuedOn;

    @Column(name = "reason", length = CODE_LENGTH, nullable = false)
    private Reason reason;

    @Column(name = "asked_by", length = NAME_LENGTH, nullable = false)
    private String askedBy;

    @Column(name = "issued_by", length = NAME_LENGTH, nullable = false)
    private String issuedBy;

    @Column(name = "note", length = TEXT_LENGTH, nullable = false)
    private String note = "";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "permit",
            nullable = false,
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_reprints_permit_permits"))
    private PermitRow permitRow;

    protected ReprintRow() {}

    public static ReprintRow of(Reprint reprint) {
        ReprintRow row = new ReprintRow();
        row.permit = reprint.permit();
        row.copy = reprint.copy();
        row.issuedOn = reprint.on();
        row.reason = reprint.reason();
        row.askedBy = reprint.askedBy();
        row.issuedBy = reprint.issuedBy();
        row.note = reprint.note();
        return row;
    }

    public String getPermit() {
        return permit;
    }

    public int getCopy() {
        return copy;
    }

    public LocalDate getIssuedOn() {
        return issuedOn;
    }

    public Reason getReason() {
        return reason;
    }

    public String getAskedBy() {
        return askedBy;
    }

    public String getIssuedBy() {
        return issuedBy;
    }

    public String getNote() {
        return note;
    }

    public Reprint toDomain() {
        return new Reprint(permit, copy, issuedOn, reason, askedBy, issuedBy, note == null ? "" : note);
    }

    @Override
    public String toString() {
        return "<ReprintRow " + permit + " copy " + copy + ">";
    }
}
