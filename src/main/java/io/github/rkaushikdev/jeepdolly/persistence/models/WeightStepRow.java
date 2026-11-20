package io.github.rkaushikdev.jeepdolly.persistence.models;

import static io.github.rkaushikdev.jeepdolly.persistence.Columns.NAME_LENGTH;

import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.Pounds;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Optional;

/** One band of overage under one schedule, and what each block of weight in it costs. */
@Entity
@Table(name = "fee_weight_steps")
@IdClass(WeightStepRow.Key.class)
public class WeightStepRow {

    /** A schedule's day and the band's place in it, from zero. */
    public record Key(LocalDate effective, int position) {}

    @Id
    @Column(name = "effective", nullable = false)
    private LocalDate effective;

    @Id
    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "up_to")
    private Pounds upTo;

    @Column(name = "per_block")
    private Dollars perBlock;

    @Column(name = "note", length = NAME_LENGTH, nullable = false)
    private String note = "";

    protected WeightStepRow() {}

    /** @param upTo the ceiling of the band, or null for the last band, which has none */
    public WeightStepRow(LocalDate effective, int position, Pounds upTo, Dollars perBlock) {
        this.effective = effective;
        this.position = position;
        this.upTo = upTo;
        this.perBlock = perBlock;
    }

    public LocalDate getEffective() {
        return effective;
    }

    public int getPosition() {
        return position;
    }

    /** The ceiling of the band, or empty for the open band at the top. */
    public Optional<Pounds> getUpTo() {
        return Optional.ofNullable(upTo);
    }

    public Dollars getPerBlock() {
        return perBlock;
    }

    public String getNote() {
        return note;
    }

    @Override
    public String toString() {
        return "<WeightStepRow " + effective + " " + position + ">";
    }
}
