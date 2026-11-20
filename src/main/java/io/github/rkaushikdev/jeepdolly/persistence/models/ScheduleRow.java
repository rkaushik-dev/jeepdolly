package io.github.rkaushikdev.jeepdolly.persistence.models;

import io.github.rkaushikdev.jeepdolly.Dollars;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** One fee schedule's flat charges, effective from one day; its other tables hang off the day. */
@Entity
@Table(name = "fee_schedules")
public class ScheduleRow {

    @Id
    @Column(name = "effective", nullable = false)
    private LocalDate effective;

    @Column(name = "review")
    private Dollars review;

    @Column(name = "amendment")
    private Dollars amendment;

    @Column(name = "minimum")
    private Dollars minimum;

    @Column(name = "annual")
    private Dollars annual;

    protected ScheduleRow() {}

    public ScheduleRow(LocalDate effective, Dollars review, Dollars amendment, Dollars minimum, Dollars annual) {
        this.effective = effective;
        this.review = review;
        this.amendment = amendment;
        this.minimum = minimum;
        this.annual = annual;
    }

    public LocalDate getEffective() {
        return effective;
    }

    public Dollars getReview() {
        return review;
    }

    public Dollars getAmendment() {
        return amendment;
    }

    public Dollars getMinimum() {
        return minimum;
    }

    public Dollars getAnnual() {
        return annual;
    }

    @Override
    public String toString() {
        return "<ScheduleRow " + effective + ">";
    }
}
