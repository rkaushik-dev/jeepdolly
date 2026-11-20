package io.github.rkaushikdev.jeepdolly.persistence.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * The next sequence for one year and one series of permit numbers.
 *
 * <p>Read for update, incremented and written in the same transaction as the permit that takes the
 * number, so a crash between the two leaves both undone and the run of numbers stays gapless.
 */
@Entity
@Table(name = "number_counters")
@IdClass(CounterRow.Key.class)
public class CounterRow {

    /** A year and a series letter. */
    public record Key(int year, String series) {}

    @Id
    @Column(name = "year", nullable = false)
    private int year;

    @Id
    @Column(name = "series", length = 2, nullable = false)
    private String series;

    @Column(name = "next_sequence", nullable = false)
    private int nextSequence = 1;

    protected CounterRow() {}

    public CounterRow(int year, String series, int nextSequence) {
        this.year = year;
        this.series = series;
        this.nextSequence = nextSequence;
    }

    public int getYear() {
        return year;
    }

    public String getSeries() {
        return series;
    }

    public int getNextSequence() {
        return nextSequence;
    }

    public void setNextSequence(int nextSequence) {
        this.nextSequence = nextSequence;
    }

    @Override
    public String toString() {
        return "<CounterRow " + year + " " + series + " at " + nextSequence + ">";
    }
}
