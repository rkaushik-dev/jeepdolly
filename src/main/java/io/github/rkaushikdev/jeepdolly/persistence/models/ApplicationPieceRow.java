package io.github.rkaushikdev.jeepdolly.persistence.models;

import static io.github.rkaushikdev.jeepdolly.persistence.Columns.CODE_LENGTH;
import static io.github.rkaushikdev.jeepdolly.persistence.Columns.NAME_LENGTH;

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

/** One thing on the trailer, by commodity code. */
@Entity
@Table(name = "application_pieces")
@IdClass(ApplicationPieceRow.Key.class)
public class ApplicationPieceRow {

    /** An application's reference and the piece's place on the list, from zero. */
    public record Key(String reference, int position) {}

    @Id
    @Column(name = "reference", length = CODE_LENGTH, nullable = false)
    private String reference;

    @Id
    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "commodity", length = CODE_LENGTH, nullable = false)
    private String commodity;

    @Column(name = "description", length = NAME_LENGTH, nullable = false)
    private String description;

    @Column(name = "serial", length = CODE_LENGTH)
    private String serial;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "reference",
            nullable = false,
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_application_pieces_reference_applications"))
    private ApplicationRow application;

    /** Declares the foreign key on {@code commodity}. Written through the commodity column. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "commodity",
            nullable = false,
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_application_pieces_commodity_commodities"))
    private CommodityRow commodityLine;

    protected ApplicationPieceRow() {}

    /**
     * A piece for {@link ApplicationRow#addPiece}.
     *
     * @param serial the serial number stamped on it, or null when it has none
     */
    public ApplicationPieceRow(int position, String commodity, String description, String serial) {
        this.position = position;
        this.commodity = commodity;
        this.description = description;
        this.serial = serial;
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

    public String getCommodity() {
        return commodity;
    }

    public String getDescription() {
        return description;
    }

    public Optional<String> getSerial() {
        return Optional.ofNullable(serial);
    }

    @Override
    public String toString() {
        return "<ApplicationPieceRow " + reference + " " + description + ">";
    }
}
