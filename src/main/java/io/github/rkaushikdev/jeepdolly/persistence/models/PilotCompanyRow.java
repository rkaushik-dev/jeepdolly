package io.github.rkaushikdev.jeepdolly.persistence.models;

import static io.github.rkaushikdev.jeepdolly.persistence.Columns.CODE_LENGTH;
import static io.github.rkaushikdev.jeepdolly.persistence.Columns.NAME_LENGTH;
import static io.github.rkaushikdev.jeepdolly.persistence.Columns.TEXT_LENGTH;

import io.github.rkaushikdev.jeepdolly.escorts.Company;
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

/**
 * One pilot car company, edited in place.
 *
 * <p>The company suspension is a flag rather than a dated order, which is not the shape the carrier
 * suspensions use. That is deliberate and a known shortcoming: the office has never needed the
 * history, and the day it does this becomes a third table like the carrier one.
 */
@Entity
@Table(name = "pilot_companies")
public class PilotCompanyRow {

    @Id
    @Column(name = "ident", length = CODE_LENGTH, nullable = false)
    private String ident;

    @Column(name = "name", length = NAME_LENGTH, nullable = false)
    private String name;

    @Column(name = "town", length = NAME_LENGTH, nullable = false)
    private String town;

    @Column(name = "telephone", length = CODE_LENGTH, nullable = false)
    private String telephone = "";

    @Column(name = "suspended", nullable = false)
    private boolean suspended;

    @Column(name = "note", length = TEXT_LENGTH, nullable = false)
    private String note = "";

    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("card")
    private List<PilotCardRow> cards = new ArrayList<>();

    protected PilotCompanyRow() {}

    public static PilotCompanyRow of(Company company) {
        PilotCompanyRow row = new PilotCompanyRow();
        row.ident = company.ident();
        row.name = company.name();
        row.town = company.town();
        row.telephone = company.telephone();
        row.suspended = company.suspended();
        row.note = company.note();
        return row;
    }

    public String getIdent() {
        return ident;
    }

    public String getName() {
        return name;
    }

    public String getTown() {
        return town;
    }

    public String getTelephone() {
        return telephone;
    }

    public boolean isSuspended() {
        return suspended;
    }

    public void setSuspended(boolean suspended) {
        this.suspended = suspended;
    }

    public String getNote() {
        return note;
    }

    /** The cards issued to this company's operators, by card, as read back from the database. */
    public List<PilotCardRow> getCards() {
        return Collections.unmodifiableList(cards);
    }

    public Company toDomain() {
        return new Company(ident, name, town, telephone, suspended, note);
    }

    @Override
    public String toString() {
        return "<PilotCompanyRow " + ident + ">";
    }
}
