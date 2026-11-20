package io.github.rkaushikdev.jeepdolly.persistence.models;

import static io.github.rkaushikdev.jeepdolly.persistence.Columns.CODE_LENGTH;
import static io.github.rkaushikdev.jeepdolly.persistence.Columns.NAME_LENGTH;

import io.github.rkaushikdev.jeepdolly.domain.Commodity;
import io.github.rkaushikdev.jeepdolly.domain.Family;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Optional;

/**
 * One line of the commodity table, with who changed it and when on the row itself.
 *
 * <p>The reference tables are edited rarely, by one person, from a form, and everything else reads
 * them, so they carry their own audit columns: the question is always asked about one line and
 * never about the table. Nothing is ever deleted from here. A commodity that is no longer hauled
 * is retired with a date, and it keeps working for the permits that already name it.
 */
@Entity
@Table(name = "commodities")
public class CommodityRow {

    @Id
    @Column(name = "code", length = CODE_LENGTH, nullable = false)
    private String code;

    @Column(name = "description", length = NAME_LENGTH, nullable = false)
    private String description;

    @Column(name = "family", length = 32, nullable = false)
    private Family family;

    @Column(name = "divisible", nullable = false)
    private boolean divisible;

    @Column(name = "allowance", length = NAME_LENGTH)
    private String allowance;

    @Column(name = "tip_limit_degrees")
    private Integer tipLimitDegrees;

    @Column(name = "high_centre", nullable = false)
    private boolean highCentre;

    @Column(name = "retired")
    private LocalDate retired;

    @Column(name = "changed_by", length = NAME_LENGTH, nullable = false)
    private String changedBy = "";

    @Column(name = "changed_on")
    private LocalDate changedOn;

    protected CommodityRow() {}

    /** An indivisible line with no flags on it, changed by nobody in particular. */
    public CommodityRow(String code, String description, Family family) {
        this.code = code;
        this.description = description;
        this.family = family;
    }

    /** The row for a commodity line, changed by nobody in particular. */
    public static CommodityRow of(Commodity commodity) {
        return of(commodity, "", null);
    }

    /**
     * The row for a commodity line, changed by {@code by} on {@code on}.
     *
     * @param on the day it changed, or null when nobody wrote it down
     */
    public static CommodityRow of(Commodity commodity, String by, LocalDate on) {
        CommodityRow row = new CommodityRow(commodity.code(), commodity.description(), commodity.family());
        row.divisible = commodity.divisible();
        row.allowance = commodity.allowance().orElse(null);
        row.tipLimitDegrees = commodity.tipLimitDegrees().orElse(null);
        row.highCentre = commodity.highCentre();
        row.changedBy = by;
        row.changedOn = on;
        return row;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public Family getFamily() {
        return family;
    }

    public boolean isDivisible() {
        return divisible;
    }

    public Optional<String> getAllowance() {
        return Optional.ofNullable(allowance);
    }

    public Optional<Integer> getTipLimitDegrees() {
        return Optional.ofNullable(tipLimitDegrees);
    }

    /** The tip limit in whole degrees, or null for none. */
    public void setTipLimitDegrees(Integer tipLimitDegrees) {
        this.tipLimitDegrees = tipLimitDegrees;
    }

    public boolean isHighCentre() {
        return highCentre;
    }

    /** The day it stopped being hauled, if it has. */
    public Optional<LocalDate> getRetired() {
        return Optional.ofNullable(retired);
    }

    public void setRetired(LocalDate retired) {
        this.retired = retired;
    }

    /** Whether it is still hauled. */
    public boolean isLive() {
        return retired == null;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public Optional<LocalDate> getChangedOn() {
        return Optional.ofNullable(changedOn);
    }

    public Commodity toDomain() {
        return new Commodity(
                code,
                description,
                family,
                divisible,
                Optional.ofNullable(allowance),
                Optional.ofNullable(tipLimitDegrees),
                highCentre);
    }

    @Override
    public String toString() {
        return "<CommodityRow " + code + ">";
    }
}
