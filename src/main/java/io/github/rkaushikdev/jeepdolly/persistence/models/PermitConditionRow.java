package io.github.rkaushikdev.jeepdolly.persistence.models;

import static io.github.rkaushikdev.jeepdolly.persistence.Columns.CODE_LENGTH;
import static io.github.rkaushikdev.jeepdolly.persistence.Columns.TEXT_LENGTH;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One rendered condition, in the words it was printed in. */
@Entity
@Table(name = "permit_conditions")
@IdClass(PermitConditionRow.Key.class)
public class PermitConditionRow {

    /** A permit's number and the condition's place on its face, from zero. */
    public record Key(String number, int position) {}

    @Id
    @Column(name = "number", length = CODE_LENGTH, nullable = false)
    private String number;

    @Id
    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "code", length = CODE_LENGTH, nullable = false)
    private String code;

    @Column(name = "category", length = CODE_LENGTH, nullable = false)
    private String category;

    @Column(name = "body", length = TEXT_LENGTH, nullable = false)
    private String body;

    @Column(name = "print_order", nullable = false)
    private int printOrder = ConditionTextRow.PRINT_ORDER;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "number",
            nullable = false,
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_permit_conditions_number_permits"))
    private PermitRow permit;

    protected PermitConditionRow() {}

    /** A condition that prints in the middle of the face, for {@link PermitRow#addCondition}. */
    public PermitConditionRow(int position, String code, String category, String body) {
        this(position, code, category, body, ConditionTextRow.PRINT_ORDER);
    }

    /** @param category the category's stored value, such as {@code travel} */
    public PermitConditionRow(int position, String code, String category, String body, int printOrder) {
        this.position = position;
        this.code = code;
        this.category = category;
        this.body = body;
        this.printOrder = printOrder;
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

    public String getCode() {
        return code;
    }

    public String getCategory() {
        return category;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public int getPrintOrder() {
        return printOrder;
    }

    @Override
    public String toString() {
        return "<PermitConditionRow " + number + " " + code + ">";
    }
}
