package io.github.rkaushikdev.jeepdolly.persistence.models;

import static io.github.rkaushikdev.jeepdolly.persistence.Columns.CODE_LENGTH;
import static io.github.rkaushikdev.jeepdolly.persistence.Columns.NAME_LENGTH;
import static io.github.rkaushikdev.jeepdolly.persistence.Columns.TEXT_LENGTH;

import io.github.rkaushikdev.jeepdolly.escorts.Endorsement;
import io.github.rkaushikdev.jeepdolly.escorts.Operator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One pilot car certificate, as issued. Renewed by a new row, never edited except to be suspended.
 *
 * <p>Endorsements are one sorted, comma separated column rather than a second table. There are two
 * of them and have been since the order was made, and a join table for a set that never grows past
 * two is a table nobody remembers to read.
 */
@Entity
@Table(
        name = "pilot_cards",
        indexes = {
            @Index(name = "ix_pilot_cards_company_ident", columnList = "company_ident"),
            @Index(name = "ix_pilot_cards_expires", columnList = "expires"),
            @Index(name = "ix_pilot_cards_name", columnList = "name")
        })
public class PilotCardRow {

    /** How the endorsement set is written into the one column that holds it. */
    public static final String SEPARATOR = ",";

    @Id
    @Column(name = "card", length = CODE_LENGTH, nullable = false)
    private String card;

    @Column(name = "name", length = NAME_LENGTH, nullable = false)
    private String name;

    @Column(name = "company_ident", length = CODE_LENGTH, nullable = false)
    private String companyIdent;

    @Column(name = "issued", nullable = false)
    private LocalDate issued;

    @Column(name = "expires", nullable = false)
    private LocalDate expires;

    @Column(name = "endorsements", length = TEXT_LENGTH, nullable = false)
    private String endorsements = "";

    @Column(name = "suspended", nullable = false)
    private boolean suspended;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "company_ident",
            nullable = false,
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_pilot_cards_company_ident_pilot_companies"))
    private PilotCompanyRow company;

    protected PilotCardRow() {}

    /** The endorsement set as one string sorted by value, for the column. */
    public static String pack(Set<Endorsement> endorsements) {
        return endorsements.stream().map(Endorsement::value).sorted().collect(Collectors.joining(SEPARATOR));
    }

    /**
     * The column back into a set, ignoring anything the code does not know.
     *
     * <p>An unknown endorsement is dropped rather than raised. The office adds one by writing rows
     * first and shipping the code that reads them afterwards, and a deployment order that has to be
     * exactly right is one that will not be exactly right.
     */
    public static Set<Endorsement> unpack(String written) {
        Set<Endorsement> found = EnumSet.noneOf(Endorsement.class);
        for (String part : written.split(SEPARATOR, -1)) {
            String cleaned = part.strip();
            if (cleaned.isEmpty()) {
                continue;
            }
            for (Endorsement one : Endorsement.values()) {
                if (one.value().equals(cleaned)) {
                    found.add(one);
                }
            }
        }
        return Set.copyOf(found);
    }

    public static PilotCardRow of(Operator operator) {
        PilotCardRow row = new PilotCardRow();
        row.card = operator.card();
        row.name = operator.name();
        row.companyIdent = operator.company();
        row.issued = operator.issued();
        row.expires = operator.expires();
        row.endorsements = pack(operator.endorsements());
        row.suspended = operator.suspended();
        return row;
    }

    public String getCard() {
        return card;
    }

    public String getName() {
        return name;
    }

    public String getCompanyIdent() {
        return companyIdent;
    }

    public LocalDate getIssued() {
        return issued;
    }

    public LocalDate getExpires() {
        return expires;
    }

    public String getEndorsements() {
        return endorsements;
    }

    public boolean isSuspended() {
        return suspended;
    }

    public void setSuspended(boolean suspended) {
        this.suspended = suspended;
    }

    public Operator toDomain() {
        return new Operator(card, name, companyIdent, issued, expires, unpack(endorsements), suspended);
    }

    @Override
    public String toString() {
        return "<PilotCardRow " + card + ">";
    }
}
