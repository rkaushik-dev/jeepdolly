package io.github.rkaushikdev.jeepdolly.persistence.models;

import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.domain.HighwayClass;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** What a mile costs on one class of road, under one schedule. */
@Entity
@Table(name = "fee_per_mile")
@IdClass(MileFeeRow.Key.class)
public class MileFeeRow {

    /** A schedule's day and a class of road by its stored value. */
    public record Key(LocalDate effective, String highwayClass) {}

    @Id
    @Column(name = "effective", nullable = false)
    private LocalDate effective;

    @Id
    @Column(name = "highway_class", length = 32, nullable = false)
    private String highwayClass;

    @Column(name = "amount")
    private Dollars amount;

    protected MileFeeRow() {}

    public MileFeeRow(LocalDate effective, HighwayClass highwayClass, Dollars amount) {
        this.effective = effective;
        this.highwayClass = highwayClass.value();
        this.amount = amount;
    }

    public LocalDate getEffective() {
        return effective;
    }

    public HighwayClass getHighwayClass() {
        return HighwayClass.of(highwayClass);
    }

    public Dollars getAmount() {
        return amount;
    }

    @Override
    public String toString() {
        return "<MileFeeRow " + highwayClass + " " + effective + ">";
    }
}
