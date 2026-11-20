package io.github.rkaushikdev.jeepdolly.persistence.models;

import static io.github.rkaushikdev.jeepdolly.persistence.Columns.CODE_LENGTH;
import static io.github.rkaushikdev.jeepdolly.persistence.Columns.NAME_LENGTH;
import static io.github.rkaushikdev.jeepdolly.persistence.Columns.TEXT_LENGTH;

import io.github.rkaushikdev.jeepdolly.SeasonKind;
import io.github.rkaushikdev.jeepdolly.SeasonOrder;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * One signed seasonal order, stored like the signed document it is.
 *
 * <p>The order number is the key because that is what the district writes on the paper and what
 * everybody quotes on the telephone. Two orders of one region may not overlap, and the database
 * cannot say so in a constraint; the season book checks it, and the table carries an index on
 * region and start so the check is a lookup. A lifted order keeps its row with the day it lifted,
 * because a permit issued under it is priced against a limit only that row explains.
 */
@Entity
@Table(name = "season_orders")
public class SeasonOrderRow {

    @Id
    @Column(name = "number", length = CODE_LENGTH, nullable = false)
    private String number;

    @Column(name = "region", length = NAME_LENGTH, nullable = false)
    private String region;

    @Column(name = "kind", length = 32, nullable = false)
    private SeasonKind kind;

    @Column(name = "starts", nullable = false)
    private LocalDate starts;

    @Column(name = "ends", nullable = false)
    private LocalDate ends;

    @Column(name = "share", nullable = false)
    private int share;

    @Column(name = "signed_by", length = NAME_LENGTH, nullable = false)
    private String signedBy = "";

    @Column(name = "note", length = TEXT_LENGTH, nullable = false)
    private String note = "";

    protected SeasonOrderRow() {}

    public SeasonOrderRow(String number, String region, SeasonKind kind, LocalDate starts, LocalDate ends, int share) {
        this.number = number;
        this.region = region;
        this.kind = kind;
        this.starts = starts;
        this.ends = ends;
        this.share = share;
    }

    /** The row for an order signed by nobody in particular, with no note. */
    public static SeasonOrderRow of(SeasonOrder order) {
        return of(order, "", "");
    }

    public static SeasonOrderRow of(SeasonOrder order, String signedBy, String note) {
        SeasonOrderRow row = new SeasonOrderRow(
                order.number(), order.region(), order.kind(), order.start(), order.end(), order.share());
        row.signedBy = signedBy;
        row.note = note;
        return row;
    }

    public String getNumber() {
        return number;
    }

    public String getRegion() {
        return region;
    }

    public SeasonKind getKind() {
        return kind;
    }

    public LocalDate getStarts() {
        return starts;
    }

    public LocalDate getEnds() {
        return ends;
    }

    public void setEnds(LocalDate ends) {
        this.ends = ends;
    }

    public int getShare() {
        return share;
    }

    public String getSignedBy() {
        return signedBy;
    }

    public String getNote() {
        return note;
    }

    public SeasonOrder toDomain() {
        return new SeasonOrder(number, region, kind, starts, ends, share);
    }

    @Override
    public String toString() {
        return "<SeasonOrderRow " + number + " " + region + ">";
    }
}
