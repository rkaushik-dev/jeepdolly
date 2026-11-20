package io.github.rkaushikdev.jeepdolly.persistence.models;

import static io.github.rkaushikdev.jeepdolly.persistence.Columns.CODE_LENGTH;
import static io.github.rkaushikdev.jeepdolly.persistence.Columns.NAME_LENGTH;
import static io.github.rkaushikdev.jeepdolly.persistence.Columns.TEXT_LENGTH;

import io.github.rkaushikdev.jeepdolly.structures.ReviewState;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * One structural study, waiting for or carrying an engineer's decision.
 *
 * <p>The review tables are append only. A review is a decision an engineer signed and the moves
 * that led to it, and neither is ever rewritten. The moves carry their own position because two can
 * land on the same day and the history has to read in the order it happened.
 */
@Entity
@Table(
        name = "reviews",
        indexes = {
            @Index(name = "ix_reviews_opened", columnList = "opened"),
            @Index(name = "ix_reviews_state", columnList = "state")
        })
public class ReviewRow {

    @Id
    @Column(name = "reference", length = CODE_LENGTH, nullable = false)
    private String reference;

    @Column(name = "opened", nullable = false)
    private LocalDate opened;

    @Column(name = "state", length = 32, nullable = false)
    private ReviewState state;

    @Column(name = "engineer", length = NAME_LENGTH)
    private String engineer;

    @Column(name = "structures", length = TEXT_LENGTH, nullable = false)
    private String structures = "";

    @Column(name = "conditions", length = TEXT_LENGTH, nullable = false)
    private String conditions = "";

    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<ReviewMoveRow> moves = new ArrayList<>();

    protected ReviewRow() {}

    public ReviewRow(String reference, LocalDate opened, ReviewState state) {
        this.reference = reference;
        this.opened = opened;
        this.state = state;
    }

    public String getReference() {
        return reference;
    }

    public LocalDate getOpened() {
        return opened;
    }

    public ReviewState getState() {
        return state;
    }

    public void setState(ReviewState state) {
        this.state = state;
    }

    /** The engineer who has it, once somebody has picked it up. */
    public Optional<String> getEngineer() {
        return Optional.ofNullable(engineer);
    }

    public void setEngineer(String engineer) {
        this.engineer = engineer;
    }

    /** The structure idents under study, comma separated. */
    public String getStructures() {
        return structures;
    }

    public void setStructures(String structures) {
        this.structures = structures;
    }

    /** The conditions of a clearance, separated by a bar. */
    public String getConditions() {
        return conditions;
    }

    public void setConditions(String conditions) {
        this.conditions = conditions;
    }

    /** Every move so far, in the order it happened. Read only: write one with {@link #addMove}. */
    public List<ReviewMoveRow> getMoves() {
        return Collections.unmodifiableList(moves);
    }

    /** Append a move to the history. It goes in with the review's next flush. */
    public ReviewMoveRow addMove(ReviewMoveRow move) {
        move.belongTo(this);
        moves.add(move);
        return move;
    }

    @Override
    public String toString() {
        return "<ReviewRow " + reference + " " + state + ">";
    }
}
