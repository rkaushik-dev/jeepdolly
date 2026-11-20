package io.github.rkaushikdev.jeepdolly.persistence.models;

import static io.github.rkaushikdev.jeepdolly.persistence.Columns.NAME_LENGTH;
import static io.github.rkaushikdev.jeepdolly.persistence.Columns.TEXT_LENGTH;

import io.github.rkaushikdev.jeepdolly.domain.Suspension;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Optional;

/**
 * One order against a carrier. Ended with a date rather than deleted, so the record shows the
 * carrier was suspended for a fortnight in March and came back, which is exactly what somebody
 * will ask about in September.
 */
@Entity
@Table(name = "suspensions")
public class SuspensionRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "usdot", nullable = false)
    private int usdot;

    @Column(name = "reason", length = TEXT_LENGTH, nullable = false)
    private String reason;

    @Column(name = "from_day", nullable = false)
    private LocalDate fromDay;

    @Column(name = "until")
    private LocalDate until;

    @Column(name = "ordered_by", length = NAME_LENGTH, nullable = false)
    private String orderedBy = "";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usdot", insertable = false, updatable = false)
    private CarrierRow carrier;

    protected SuspensionRow() {}

    /** An order until further notice, by nobody in particular. */
    public SuspensionRow(int usdot, String reason, LocalDate fromDay) {
        this.usdot = usdot;
        this.reason = reason;
        this.fromDay = fromDay;
    }

    /** The row for a suspension of a carrier, ordered by nobody in particular. */
    public static SuspensionRow of(int usdot, Suspension order) {
        return of(usdot, order, "");
    }

    /** The row for a suspension of a carrier, ordered by {@code by}. */
    public static SuspensionRow of(int usdot, Suspension order, String by) {
        SuspensionRow row = new SuspensionRow(usdot, order.reason(), order.fromDay());
        row.until = order.until().orElse(null);
        row.orderedBy = by;
        return row;
    }

    void belongTo(CarrierRow owner) {
        this.carrier = owner;
        this.usdot = owner.getUsdot();
    }

    /** The serial the database gave it, once it has been written. */
    public Optional<Integer> getId() {
        return Optional.ofNullable(id);
    }

    public int getUsdot() {
        return usdot;
    }

    public String getReason() {
        return reason;
    }

    public LocalDate getFromDay() {
        return fromDay;
    }

    /** The last day it runs, or empty until further notice. */
    public Optional<LocalDate> getUntil() {
        return Optional.ofNullable(until);
    }

    /** End the order on a day, or reopen it until further notice with null. */
    public void setUntil(LocalDate until) {
        this.until = until;
    }

    public String getOrderedBy() {
        return orderedBy;
    }

    public void setOrderedBy(String orderedBy) {
        this.orderedBy = orderedBy;
    }

    public Suspension toDomain() {
        return new Suspension(reason, fromDay, Optional.ofNullable(until));
    }

    @Override
    public String toString() {
        return "<SuspensionRow " + usdot + " from " + fromDay + ">";
    }
}
