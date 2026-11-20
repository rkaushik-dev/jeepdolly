package io.github.rkaushikdev.jeepdolly.persistence.models;

import static io.github.rkaushikdev.jeepdolly.persistence.Columns.CODE_LENGTH;
import static io.github.rkaushikdev.jeepdolly.persistence.Columns.NAME_LENGTH;
import static io.github.rkaushikdev.jeepdolly.persistence.Columns.TEXT_LENGTH;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * One sentence a permit can print, as it stood on a date.
 *
 * <p>A condition that is reworded gets a new row with a new effective date, so a permit issued last
 * year still prints the words it was issued with.
 */
@Entity
@Table(name = "condition_texts")
@IdClass(ConditionTextRow.Key.class)
public class ConditionTextRow {

    /** A condition's code and the day the wording took effect. */
    public record Key(String code, LocalDate effective) {}

    /** Where a condition prints when nobody says, the middle of the face. */
    public static final int PRINT_ORDER = 50;

    @Id
    @Column(name = "code", length = CODE_LENGTH, nullable = false)
    private String code;

    @Id
    @Column(name = "effective", nullable = false)
    private LocalDate effective;

    @Column(name = "category", length = CODE_LENGTH, nullable = false)
    private String category;

    @Column(name = "body", length = TEXT_LENGTH, nullable = false)
    private String body;

    @Column(name = "print_order", nullable = false)
    private int printOrder = PRINT_ORDER;

    @Column(name = "changed_by", length = NAME_LENGTH, nullable = false)
    private String changedBy = "";

    protected ConditionTextRow() {}

    /** A wording that prints in the middle of the face, changed by nobody in particular. */
    public ConditionTextRow(String code, LocalDate effective, String category, String body) {
        this(code, effective, category, body, PRINT_ORDER);
    }

    public ConditionTextRow(String code, LocalDate effective, String category, String body, int printOrder) {
        this.code = code;
        this.effective = effective;
        this.category = category;
        this.body = body;
        this.printOrder = printOrder;
    }

    public String getCode() {
        return code;
    }

    public LocalDate getEffective() {
        return effective;
    }

    public String getCategory() {
        return category;
    }

    public String getBody() {
        return body;
    }

    public int getPrintOrder() {
        return printOrder;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(String changedBy) {
        this.changedBy = changedBy;
    }

    @Override
    public String toString() {
        return "<ConditionTextRow " + code + " " + effective + ">";
    }
}
