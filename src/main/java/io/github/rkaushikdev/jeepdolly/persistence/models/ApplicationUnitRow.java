package io.github.rkaushikdev.jeepdolly.persistence.models;

import static io.github.rkaushikdev.jeepdolly.persistence.Columns.CODE_LENGTH;
import static io.github.rkaushikdev.jeepdolly.persistence.Columns.NAME_LENGTH;

import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.domain.UnitKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One piece of equipment in the string, in road order. */
@Entity
@Table(name = "application_units")
@IdClass(ApplicationUnitRow.Key.class)
public class ApplicationUnitRow {

    /** An application's reference and the unit's place in the string, from zero. */
    public record Key(String reference, int position) {}

    @Id
    @Column(name = "reference", length = CODE_LENGTH, nullable = false)
    private String reference;

    @Id
    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "kind", length = 32, nullable = false)
    private UnitKind kind;

    @Column(name = "axles", nullable = false)
    private int axles;

    @Column(name = "plate", length = CODE_LENGTH, nullable = false)
    private String plate;

    @Column(name = "tare")
    private Pounds tare;

    @Column(name = "make", length = NAME_LENGTH, nullable = false)
    private String make = "";

    @Column(name = "unit_number", length = CODE_LENGTH, nullable = false)
    private String unitNumber = "";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reference", insertable = false, updatable = false)
    private ApplicationRow application;

    protected ApplicationUnitRow() {}

    /** A unit with no make or fleet number, for {@link ApplicationRow#addUnit}. */
    public ApplicationUnitRow(int position, UnitKind kind, int axles, String plate, Pounds tare) {
        this(position, kind, axles, plate, tare, "", "");
    }

    public ApplicationUnitRow(
            int position, UnitKind kind, int axles, String plate, Pounds tare, String make, String unitNumber) {
        this.position = position;
        this.kind = kind;
        this.axles = axles;
        this.plate = plate;
        this.tare = tare;
        this.make = make;
        this.unitNumber = unitNumber;
    }

    void belongTo(ApplicationRow owner) {
        this.application = owner;
        this.reference = owner.getReference();
    }

    public String getReference() {
        return reference;
    }

    public int getPosition() {
        return position;
    }

    public UnitKind getKind() {
        return kind;
    }

    public int getAxles() {
        return axles;
    }

    public void setAxles(int axles) {
        this.axles = axles;
    }

    public String getPlate() {
        return plate;
    }

    public Pounds getTare() {
        return tare;
    }

    public String getMake() {
        return make;
    }

    public String getUnitNumber() {
        return unitNumber;
    }

    @Override
    public String toString() {
        return "<ApplicationUnitRow " + reference + " " + kind + ">";
    }
}
