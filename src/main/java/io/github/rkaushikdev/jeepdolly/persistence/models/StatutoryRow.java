package io.github.rkaushikdev.jeepdolly.persistence.models;

import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.domain.HighwayClass;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * One class of road, one set of statutory weight limits, one effective date.
 *
 * <p>The published tables are rows because the legislature changes them, keyed by class of road
 * and by the day they take effect, exactly as the published documents are keyed. Nothing is ever
 * edited: a change is a new row with a new effective date, and a permit issued in March is priced
 * by the March row for the rest of its life.
 */
@Entity
@Table(name = "statutory_limits")
@IdClass(StatutoryRow.Key.class)
public class StatutoryRow {

    /** A class of road, by its stored value, and the day its limits took effect. */
    public record Key(String highwayClass, LocalDate effective) {}

    @Id
    @Column(name = "highway_class", length = 32, nullable = false)
    private String highwayClass;

    @Id
    @Column(name = "effective", nullable = false)
    private LocalDate effective;

    @Column(name = "single")
    private Pounds single;

    @Column(name = "tandem")
    private Pounds tandem;

    @Column(name = "tridem")
    private Pounds tridem;

    @Column(name = "quad")
    private Pounds quad;

    @Column(name = "beyond")
    private Pounds beyond;

    @Column(name = "gross")
    private Pounds gross;

    @Column(name = "tire_rate", nullable = false)
    private int tireRate;

    protected StatutoryRow() {}

    /**
     * @param beyond the allowance for each axle past the quad
     * @param tireRate pounds per inch of tire on the ground
     */
    public StatutoryRow(
            HighwayClass highwayClass,
            LocalDate effective,
            Pounds single,
            Pounds tandem,
            Pounds tridem,
            Pounds quad,
            Pounds beyond,
            Pounds gross,
            int tireRate) {
        this.highwayClass = highwayClass.value();
        this.effective = effective;
        this.single = single;
        this.tandem = tandem;
        this.tridem = tridem;
        this.quad = quad;
        this.beyond = beyond;
        this.gross = gross;
        this.tireRate = tireRate;
    }

    public HighwayClass getHighwayClass() {
        return HighwayClass.of(highwayClass);
    }

    public LocalDate getEffective() {
        return effective;
    }

    public Pounds getSingle() {
        return single;
    }

    public Pounds getTandem() {
        return tandem;
    }

    public Pounds getTridem() {
        return tridem;
    }

    public Pounds getQuad() {
        return quad;
    }

    public Pounds getBeyond() {
        return beyond;
    }

    public Pounds getGross() {
        return gross;
    }

    public int getTireRate() {
        return tireRate;
    }

    @Override
    public String toString() {
        return "<StatutoryRow " + highwayClass + " " + effective + ">";
    }
}
