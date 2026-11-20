package io.github.rkaushikdev.jeepdolly.persistence.models;

import static io.github.rkaushikdev.jeepdolly.persistence.Columns.CODE_LENGTH;

import io.github.rkaushikdev.jeepdolly.Dollars;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** What each kind of permit costs before anything is measured, under one schedule. */
@Entity
@Table(name = "fee_base")
@IdClass(BaseFeeRow.Key.class)
public class BaseFeeRow {

    /** A schedule's day and a kind of permit by its stored value. */
    public record Key(LocalDate effective, String kind) {}

    @Id
    @Column(name = "effective", nullable = false)
    private LocalDate effective;

    @Id
    @Column(name = "kind", length = CODE_LENGTH, nullable = false)
    private String kind;

    @Column(name = "amount")
    private Dollars amount;

    protected BaseFeeRow() {}

    /** @param kind the kind of permit as it is stored, such as {@code ordinary} */
    public BaseFeeRow(LocalDate effective, String kind, Dollars amount) {
        this.effective = effective;
        this.kind = kind;
        this.amount = amount;
    }

    public LocalDate getEffective() {
        return effective;
    }

    public String getKind() {
        return kind;
    }

    public Dollars getAmount() {
        return amount;
    }

    @Override
    public String toString() {
        return "<BaseFeeRow " + kind + " " + effective + ">";
    }
}
