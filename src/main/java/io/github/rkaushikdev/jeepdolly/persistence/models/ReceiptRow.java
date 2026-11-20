package io.github.rkaushikdev.jeepdolly.persistence.models;

import static io.github.rkaushikdev.jeepdolly.persistence.Columns.CODE_LENGTH;
import static io.github.rkaushikdev.jeepdolly.persistence.Columns.NAME_LENGTH;
import static io.github.rkaushikdev.jeepdolly.persistence.Columns.TEXT_LENGTH;

import io.github.rkaushikdev.jeepdolly.Dollars;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * One payment against one permit, which is the other half of a permit and was once missing.
 *
 * <p>A receipt is never edited and never deleted. A payment taken twice is refunded with a second
 * receipt for a negative amount, so the till still adds up and the mistake is visible. The method
 * matters because the drawer only holds two of them: cash and cheque are in it at the end of the
 * day, and the nightly reconciliation counts the rest apart.
 */
@Entity
@Table(
        name = "receipts",
        indexes = {
            @Index(name = "ix_receipts_method", columnList = "method"),
            @Index(name = "ix_receipts_permit", columnList = "permit"),
            @Index(name = "ix_receipts_taken", columnList = "taken")
        })
public class ReceiptRow {

    /** What the drawer holds at the end of the day. */
    public static final List<String> IN_THE_DRAWER = List.of("cash", "cheque");

    /** Everything the office will take. */
    public static final List<String> METHODS = List.of("cash", "cheque", "account", "card", "refund");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "permit", length = CODE_LENGTH, nullable = false)
    private String permit;

    @Column(name = "taken", nullable = false)
    private LocalDate taken;

    @Column(name = "method", length = CODE_LENGTH, nullable = false)
    private String method;

    @Column(name = "amount")
    private Dollars amount;

    @Column(name = "taken_by", length = NAME_LENGTH, nullable = false)
    private String takenBy;

    @Column(name = "reference", length = CODE_LENGTH, nullable = false)
    private String reference = "";

    @Column(name = "note", length = TEXT_LENGTH, nullable = false)
    private String note = "";

    /** Declares the foreign key on {@code permit}. Written through the permit column. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "permit",
            nullable = false,
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_receipts_permit_permits"))
    private PermitRow permitRow;

    protected ReceiptRow() {}

    /**
     * @param reference the cheque number or card slip, or empty
     */
    public ReceiptRow(
            String permit,
            LocalDate taken,
            String method,
            Dollars amount,
            String takenBy,
            String reference,
            String note) {
        this.permit = permit;
        this.taken = taken;
        this.method = method;
        this.amount = amount;
        this.takenBy = takenBy;
        this.reference = reference;
        this.note = note;
    }

    /** The serial the database gave it, once it has been written. */
    public Optional<Integer> getId() {
        return Optional.ofNullable(id);
    }

    public String getPermit() {
        return permit;
    }

    public LocalDate getTaken() {
        return taken;
    }

    public String getMethod() {
        return method;
    }

    public Dollars getAmount() {
        return amount;
    }

    public String getTakenBy() {
        return takenBy;
    }

    public String getReference() {
        return reference;
    }

    public String getNote() {
        return note;
    }

    /** Whether this payment is in the drawer at the end of the day. */
    public boolean isInTheDrawer() {
        return IN_THE_DRAWER.contains(method);
    }

    @Override
    public String toString() {
        return "<ReceiptRow " + permit + " " + method + " " + amount + ">";
    }
}
