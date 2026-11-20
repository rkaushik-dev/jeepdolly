package io.github.rkaushikdev.jeepdolly.persistence.models;

import static io.github.rkaushikdev.jeepdolly.persistence.Columns.CODE_LENGTH;
import static io.github.rkaushikdev.jeepdolly.persistence.Columns.NAME_LENGTH;

import io.github.rkaushikdev.jeepdolly.Dollars;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One line of what a permit cost, in the words the receipt used. */
@Entity
@Table(name = "permit_fees")
@IdClass(FeeLineRow.Key.class)
public class FeeLineRow {

    /** A permit's number and the line's place on the receipt, from zero. */
    public record Key(String number, int position) {}

    @Id
    @Column(name = "number", length = CODE_LENGTH, nullable = false)
    private String number;

    @Id
    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "description", length = NAME_LENGTH, nullable = false)
    private String description;

    @Column(name = "amount")
    private Dollars amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "number",
            nullable = false,
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_permit_fees_number_permits"))
    private PermitRow permit;

    protected FeeLineRow() {}

    /** A fee line for {@link PermitRow#addFee}. */
    public FeeLineRow(int position, String description, Dollars amount) {
        this.position = position;
        this.description = description;
        this.amount = amount;
    }

    void belongTo(PermitRow owner) {
        this.permit = owner;
        this.number = owner.getNumber();
    }

    public String getNumber() {
        return number;
    }

    public int getPosition() {
        return position;
    }

    public String getDescription() {
        return description;
    }

    public Dollars getAmount() {
        return amount;
    }

    @Override
    public String toString() {
        return "<FeeLineRow " + number + " " + description + ">";
    }
}
