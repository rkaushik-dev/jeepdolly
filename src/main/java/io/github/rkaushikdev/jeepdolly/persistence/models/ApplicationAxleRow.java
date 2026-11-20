package io.github.rkaushikdev.jeepdolly.persistence.models;

import static io.github.rkaushikdev.jeepdolly.persistence.Columns.CODE_LENGTH;

import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.domain.AxleKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Optional;

/** One axle of the declared combination, with the gap in front of it. */
@Entity
@Table(name = "application_axles")
@IdClass(ApplicationAxleRow.Key.class)
public class ApplicationAxleRow {

    /** An application's reference and the axle's place in road order, from zero. */
    public record Key(String reference, int position) {}

    @Id
    @Column(name = "reference", length = CODE_LENGTH, nullable = false)
    private String reference;

    @Id
    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "kind", length = 32, nullable = false)
    private AxleKind kind;

    @Column(name = "weight")
    private Pounds weight;

    @Column(name = "tires", nullable = false)
    private int tires = 4;

    @Column(name = "tire_width")
    private Inches tireWidth;

    @Column(name = "liftable", nullable = false)
    private boolean liftable;

    @Column(name = "spacing_before")
    private Inches spacingBefore;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "reference",
            nullable = false,
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_application_axles_reference_applications"))
    private ApplicationRow application;

    protected ApplicationAxleRow() {}

    /**
     * An axle for {@link ApplicationRow#addAxle}, which fills in the application it belongs to.
     *
     * @param spacingBefore the gap to the axle in front, or null for the first axle
     */
    public ApplicationAxleRow(
            int position,
            AxleKind kind,
            Pounds weight,
            int tires,
            Inches tireWidth,
            boolean liftable,
            Inches spacingBefore) {
        this.position = position;
        this.kind = kind;
        this.weight = weight;
        this.tires = tires;
        this.tireWidth = tireWidth;
        this.liftable = liftable;
        this.spacingBefore = spacingBefore;
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

    public AxleKind getKind() {
        return kind;
    }

    public Pounds getWeight() {
        return weight;
    }

    public int getTires() {
        return tires;
    }

    public void setTires(int tires) {
        this.tires = tires;
    }

    public Inches getTireWidth() {
        return tireWidth;
    }

    public boolean isLiftable() {
        return liftable;
    }

    /** The gap to the axle in front; empty for the first axle, which has nothing in front of it. */
    public Optional<Inches> getSpacingBefore() {
        return Optional.ofNullable(spacingBefore);
    }

    @Override
    public String toString() {
        return "<ApplicationAxleRow " + reference + " " + position + ">";
    }
}
