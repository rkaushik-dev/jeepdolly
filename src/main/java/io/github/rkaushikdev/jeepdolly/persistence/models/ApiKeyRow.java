package io.github.rkaushikdev.jeepdolly.persistence.models;

import static io.github.rkaushikdev.jeepdolly.persistence.Columns.CODE_LENGTH;
import static io.github.rkaushikdev.jeepdolly.persistence.Columns.NAME_LENGTH;
import static io.github.rkaushikdev.jeepdolly.persistence.Columns.TEXT_LENGTH;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Optional;

/**
 * One api key, its owner, and what it is allowed to do.
 *
 * <p>A key is issued to a person, not to a company and not to a machine, because the office wants
 * a name against every permit written through the interface. The key itself is never stored: the
 * table holds a hash, so a copy of the database is not a copy of everybody's credentials. A revoked
 * key is kept, because the permits written with it are still on file with the name attached.
 */
@Entity
@Table(name = "api_keys")
public class ApiKeyRow {

    /** A sha256 in hexadecimal. */
    public static final int DIGEST_LENGTH = 64;

    @Id
    @Column(name = "ident", length = CODE_LENGTH, nullable = false)
    private String ident;

    @Column(name = "digest", length = DIGEST_LENGTH, nullable = false)
    private String digest;

    @Column(name = "person", length = NAME_LENGTH, nullable = false)
    private String person;

    @Column(name = "role", length = CODE_LENGTH, nullable = false)
    private String role;

    @Column(name = "usdot")
    private Integer usdot;

    @Column(name = "issued", nullable = false)
    private LocalDate issued;

    @Column(name = "revoked")
    private LocalDate revoked;

    @Column(name = "note", length = TEXT_LENGTH, nullable = false)
    private String note = "";

    protected ApiKeyRow() {}

    /**
     * @param role the role's stored value, such as {@code clerk}
     * @param usdot the carrier a carrier's own key is limited to, or null for an office key
     */
    public ApiKeyRow(
            String ident, String digest, String person, String role, Integer usdot, LocalDate issued, String note) {
        this.ident = ident;
        this.digest = digest;
        this.person = person;
        this.role = role;
        this.usdot = usdot;
        this.issued = issued;
        this.note = note;
    }

    public String getIdent() {
        return ident;
    }

    public String getDigest() {
        return digest;
    }

    public String getPerson() {
        return person;
    }

    public String getRole() {
        return role;
    }

    /** The carrier a carrier's own key is limited to; empty for an office key. */
    public Optional<Integer> getUsdot() {
        return Optional.ofNullable(usdot);
    }

    public LocalDate getIssued() {
        return issued;
    }

    public Optional<LocalDate> getRevoked() {
        return Optional.ofNullable(revoked);
    }

    public void setRevoked(LocalDate revoked) {
        this.revoked = revoked;
    }

    public String getNote() {
        return note;
    }

    /** Whether the key works on a day: issued by then and not yet revoked. */
    public boolean liveOn(LocalDate day) {
        if (day.isBefore(issued)) {
            return false;
        }
        return revoked == null || day.isBefore(revoked);
    }

    @Override
    public String toString() {
        return "<ApiKeyRow " + ident + " " + role + ">";
    }
}
