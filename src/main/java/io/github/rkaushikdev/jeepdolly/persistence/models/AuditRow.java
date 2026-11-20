package io.github.rkaushikdev.jeepdolly.persistence.models;

import static io.github.rkaushikdev.jeepdolly.persistence.Columns.CODE_LENGTH;
import static io.github.rkaushikdev.jeepdolly.persistence.Columns.NAME_LENGTH;
import static io.github.rkaushikdev.jeepdolly.persistence.Columns.TEXT_LENGTH;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * One thing that happened, written once and never touched again.
 *
 * <p>The trail is append only, and nothing in this system has a method that edits a trail row. An
 * action is a dotted word from a fixed vocabulary, not a sentence, because the trail is grepped and
 * counted. There is no foreign key to the document on purpose: the trail outlives the things it
 * describes, and a trail row a cascade could delete is not a trail.
 */
@Entity
@Table(
        name = "audit_trail",
        indexes = {
            @Index(name = "ix_audit_trail_action", columnList = "action"),
            @Index(name = "ix_audit_trail_actor", columnList = "actor"),
            @Index(name = "ix_audit_trail_happened", columnList = "happened"),
            @Index(name = "ix_audit_trail_reference", columnList = "reference")
        })
public class AuditRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "happened", nullable = false)
    private LocalDateTime happened;

    @Column(name = "action", length = CODE_LENGTH, nullable = false)
    private String action;

    @Column(name = "actor", length = NAME_LENGTH, nullable = false)
    private String actor;

    @Column(name = "kind", length = CODE_LENGTH, nullable = false)
    private String kind;

    @Column(name = "reference", length = CODE_LENGTH, nullable = false)
    private String reference;

    @Column(name = "detail", length = TEXT_LENGTH, nullable = false)
    private String detail = "";

    protected AuditRow() {}

    /**
     * @param kind what sort of document it happened to, such as {@code permit}
     * @param reference which one
     */
    public AuditRow(LocalDateTime happened, String action, String actor, String kind, String reference, String detail) {
        this.happened = happened;
        this.action = action;
        this.actor = actor;
        this.kind = kind;
        this.reference = reference;
        this.detail = detail;
    }

    /** The serial the database gave it, once it has been written. */
    public Optional<Integer> getId() {
        return Optional.ofNullable(id);
    }

    public LocalDateTime getHappened() {
        return happened;
    }

    public String getAction() {
        return action;
    }

    public String getActor() {
        return actor;
    }

    public String getKind() {
        return kind;
    }

    public String getReference() {
        return reference;
    }

    public String getDetail() {
        return detail;
    }

    @Override
    public String toString() {
        return "<AuditRow " + action + " " + kind + ":" + reference + ">";
    }
}
