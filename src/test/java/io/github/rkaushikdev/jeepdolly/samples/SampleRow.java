package io.github.rkaushikdev.jeepdolly.samples;

import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.Miles;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.domain.HighwayClass;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * A table that exists only to prove the column types round trip.
 *
 * <p>It lives outside the persistence package on purpose, so the office's own entity scan never
 * sees it and the shipped schema is never asked to hold it.
 */
@Entity
@Table(name = "sample_measures")
public class SampleRow {

    @Id
    @Column(name = "ident", length = 16, nullable = false)
    private String ident;

    @Column(name = "weight")
    private Pounds weight;

    @Column(name = "size")
    private Inches size;

    @Column(name = "distance")
    private Miles distance;

    @Column(name = "fee")
    private Dollars fee;

    @Column(name = "road", length = 32)
    private HighwayClass road;

    @Column(name = "issued_on")
    private LocalDate day;

    @Column(name = "spare")
    private Pounds spare;

    @Column(name = "spare_road", length = 32)
    private HighwayClass spareRoad;

    protected SampleRow() {}

    public SampleRow(
            String ident, Pounds weight, Inches size, Miles distance, Dollars fee, HighwayClass road, LocalDate day) {
        this.ident = ident;
        this.weight = weight;
        this.size = size;
        this.distance = distance;
        this.fee = fee;
        this.road = road;
        this.day = day;
    }

    public String ident() {
        return ident;
    }

    public Pounds weight() {
        return weight;
    }

    public Inches size() {
        return size;
    }

    public Miles distance() {
        return distance;
    }

    public Dollars fee() {
        return fee;
    }

    public HighwayClass road() {
        return road;
    }

    public LocalDate day() {
        return day;
    }

    /** Null when nothing was stored, which the test is about. */
    public Pounds spare() {
        return spare;
    }

    /** Null when nothing was stored, which the test is about. */
    public HighwayClass spareRoad() {
        return spareRoad;
    }
}
