package io.github.rkaushikdev.jeepdolly.persistence.models;

import static io.github.rkaushikdev.jeepdolly.persistence.Columns.CODE_LENGTH;
import static io.github.rkaushikdev.jeepdolly.persistence.Columns.NAME_LENGTH;
import static io.github.rkaushikdev.jeepdolly.persistence.Columns.TEXT_LENGTH;

import io.github.rkaushikdev.jeepdolly.structures.ReviewState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** One thing that happened to a review, in the order it happened. */
@Entity
@Table(name = "review_moves")
@IdClass(ReviewMoveRow.Key.class)
public class ReviewMoveRow {

    /** A review's reference and the move's place in its history. */
    public record Key(String reference, int position) {}

    @Id
    @Column(name = "reference", length = CODE_LENGTH, nullable = false)
    private String reference;

    @Id
    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "happened", nullable = false)
    private LocalDate happened;

    @Column(name = "who", length = NAME_LENGTH, nullable = false)
    private String who;

    @Column(name = "state", length = 32, nullable = false)
    private ReviewState state;

    @Column(name = "note", length = TEXT_LENGTH, nullable = false)
    private String note = "";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "reference",
            nullable = false,
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_review_moves_reference_reviews"))
    private ReviewRow review;

    protected ReviewMoveRow() {}

    /** A move for {@link ReviewRow#addMove}, which fills in the review it belongs to. */
    public ReviewMoveRow(int position, LocalDate happened, String who, ReviewState state, String note) {
        this.position = position;
        this.happened = happened;
        this.who = who;
        this.state = state;
        this.note = note;
    }

    /** A move written straight against a review reference, with no note. */
    public ReviewMoveRow(String reference, int position, LocalDate happened, String who, ReviewState state) {
        this(position, happened, who, state, "");
        this.reference = reference;
    }

    void belongTo(ReviewRow owner) {
        this.review = owner;
        this.reference = owner.getReference();
    }

    public String getReference() {
        return reference;
    }

    public int getPosition() {
        return position;
    }

    public LocalDate getHappened() {
        return happened;
    }

    public String getWho() {
        return who;
    }

    public ReviewState getState() {
        return state;
    }

    public String getNote() {
        return note;
    }

    @Override
    public String toString() {
        return "<ReviewMoveRow " + reference + " " + position + ">";
    }
}
