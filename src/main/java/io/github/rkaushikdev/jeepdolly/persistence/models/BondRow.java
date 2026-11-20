package io.github.rkaushikdev.jeepdolly.persistence.models;

import static io.github.rkaushikdev.jeepdolly.persistence.Columns.CODE_LENGTH;
import static io.github.rkaushikdev.jeepdolly.persistence.Columns.NAME_LENGTH;

import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.domain.Bond;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** One surety bond, as filed. Never edited: a renewal is a new row. */
@Entity
@Table(name = "bonds")
public class BondRow {

    @Id
    @Column(name = "number", length = CODE_LENGTH, nullable = false)
    private String number;

    @Column(name = "usdot", nullable = false)
    private int usdot;

    @Column(name = "surety", length = NAME_LENGTH, nullable = false)
    private String surety;

    @Column(name = "amount")
    private Dollars amount;

    @Column(name = "effective", nullable = false)
    private LocalDate effective;

    @Column(name = "expires", nullable = false)
    private LocalDate expires;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usdot", insertable = false, updatable = false)
    private CarrierRow carrier;

    protected BondRow() {}

    public BondRow(String number, int usdot, String surety, Dollars amount, LocalDate effective, LocalDate expires) {
        this.number = number;
        this.usdot = usdot;
        this.surety = surety;
        this.amount = amount;
        this.effective = effective;
        this.expires = expires;
    }

    /** The row for a bond filed against a carrier. */
    public static BondRow of(int usdot, Bond bond) {
        return new BondRow(bond.number(), usdot, bond.surety(), bond.amount(), bond.effective(), bond.expires());
    }

    void belongTo(CarrierRow owner) {
        this.carrier = owner;
        this.usdot = owner.getUsdot();
    }

    public String getNumber() {
        return number;
    }

    public int getUsdot() {
        return usdot;
    }

    public String getSurety() {
        return surety;
    }

    public Dollars getAmount() {
        return amount;
    }

    public LocalDate getEffective() {
        return effective;
    }

    public LocalDate getExpires() {
        return expires;
    }

    public Bond toDomain() {
        return new Bond(number, surety, amount, effective, expires);
    }

    @Override
    public String toString() {
        return "<BondRow " + number + ">";
    }
}
