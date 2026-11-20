package io.github.rkaushikdev.jeepdolly.persistence.models;

import static io.github.rkaushikdev.jeepdolly.persistence.Columns.CODE_LENGTH;
import static io.github.rkaushikdev.jeepdolly.persistence.Columns.NAME_LENGTH;
import static io.github.rkaushikdev.jeepdolly.persistence.Columns.TEXT_LENGTH;

import io.github.rkaushikdev.jeepdolly.utilities.LineOwner;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Whoever owns a wire over the road, and how much warning they want before a load comes. */
@Entity
@Table(name = "line_owners")
public class LineOwnerRow {

    /** The working days' notice an owner wants when nobody has said otherwise. */
    public static final int NOTICE_DAYS = 5;

    @Id
    @Column(name = "ident", length = CODE_LENGTH, nullable = false)
    private String ident;

    @Column(name = "name", length = NAME_LENGTH, nullable = false)
    private String name;

    @Column(name = "telephone", length = CODE_LENGTH, nullable = false)
    private String telephone = "";

    @Column(name = "notice_days", nullable = false)
    private int noticeDays = NOTICE_DAYS;

    @Column(name = "note", length = TEXT_LENGTH, nullable = false)
    private String note = "";

    @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ident")
    private List<LineCrossingRow> wires = new ArrayList<>();

    protected LineOwnerRow() {}

    public static LineOwnerRow of(LineOwner owner) {
        LineOwnerRow row = new LineOwnerRow();
        row.ident = owner.ident();
        row.name = owner.name();
        row.telephone = owner.telephone();
        row.noticeDays = owner.noticeDays();
        row.note = owner.note();
        return row;
    }

    public String getIdent() {
        return ident;
    }

    public String getName() {
        return name;
    }

    public String getTelephone() {
        return telephone;
    }

    public int getNoticeDays() {
        return noticeDays;
    }

    public String getNote() {
        return note;
    }

    /** The wires this owner has over the road, by ident, as read back from the database. */
    public List<LineCrossingRow> getWires() {
        return Collections.unmodifiableList(wires);
    }

    public LineOwner toDomain() {
        return new LineOwner(ident, name, telephone, noticeDays, note);
    }

    @Override
    public String toString() {
        return "<LineOwnerRow " + ident + ">";
    }
}
