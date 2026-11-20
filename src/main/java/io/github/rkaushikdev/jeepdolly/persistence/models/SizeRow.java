package io.github.rkaushikdev.jeepdolly.persistence.models;

import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import io.github.rkaushikdev.jeepdolly.domain.HighwayClass;
import io.github.rkaushikdev.jeepdolly.limits.SizeTable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** The legal box and the routine box for one class of road, from one effective date. */
@Entity
@Table(name = "size_limits")
@IdClass(SizeRow.Key.class)
public class SizeRow {

    /** A class of road, by its stored value, and the day its boxes took effect. */
    public record Key(String highwayClass, LocalDate effective) {}

    @Id
    @Column(name = "highway_class", length = 32, nullable = false)
    private String highwayClass;

    @Id
    @Column(name = "effective", nullable = false)
    private LocalDate effective;

    @Column(name = "legal_width")
    private Inches legalWidth;

    @Column(name = "legal_height")
    private Inches legalHeight;

    @Column(name = "legal_length")
    private Inches legalLength;

    @Column(name = "legal_front")
    private Inches legalFront;

    @Column(name = "legal_rear")
    private Inches legalRear;

    @Column(name = "routine_width")
    private Inches routineWidth;

    @Column(name = "routine_height")
    private Inches routineHeight;

    @Column(name = "routine_length")
    private Inches routineLength;

    @Column(name = "routine_front")
    private Inches routineFront;

    @Column(name = "routine_rear")
    private Inches routineRear;

    protected SizeRow() {}

    /** The row for one class of road's two boxes, from a day. */
    public static SizeRow of(SizeTable table, LocalDate effective) {
        SizeRow row = new SizeRow();
        row.highwayClass = table.highwayClass().value();
        row.effective = effective;
        Envelope legal = table.legal();
        row.legalWidth = legal.width();
        row.legalHeight = legal.height();
        row.legalLength = legal.length();
        row.legalFront = legal.frontOverhang();
        row.legalRear = legal.rearOverhang();
        Envelope routine = table.routine();
        row.routineWidth = routine.width();
        row.routineHeight = routine.height();
        row.routineLength = routine.length();
        row.routineFront = routine.frontOverhang();
        row.routineRear = routine.rearOverhang();
        return row;
    }

    public HighwayClass getHighwayClass() {
        return HighwayClass.of(highwayClass);
    }

    public LocalDate getEffective() {
        return effective;
    }

    /** The box a load may fill without a permit. */
    public Envelope getLegal() {
        return new Envelope(legalWidth, legalHeight, legalLength, legalFront, legalRear);
    }

    /** The box a routine permit may be written for at the counter. */
    public Envelope getRoutine() {
        return new Envelope(routineWidth, routineHeight, routineLength, routineFront, routineRear);
    }

    @Override
    public String toString() {
        return "<SizeRow " + highwayClass + " " + effective + ">";
    }
}
