package io.github.rkaushikdev.jeepdolly.persistence.models;

import static io.github.rkaushikdev.jeepdolly.persistence.Columns.CODE_LENGTH;
import static io.github.rkaushikdev.jeepdolly.persistence.Columns.NAME_LENGTH;
import static io.github.rkaushikdev.jeepdolly.persistence.Columns.TEXT_LENGTH;

import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.enforcement.Assessed;
import io.github.rkaushikdev.jeepdolly.enforcement.Citation;
import io.github.rkaushikdev.jeepdolly.enforcement.CitationState;
import io.github.rkaushikdev.jeepdolly.enforcement.Offence;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Optional;

/**
 * One citation, as written and as it stands: one table and deliberately not two.
 *
 * <p>The audit trail already records every move a citation makes with the person who made it, so
 * there is no second history here. The one exception is the reason a citation was dismissed or
 * voided, which is on the row, because that one goes in a letter.
 *
 * <p>The assessment is stored flattened: the base, the multiplier and the total. A citation is a
 * demand for a specific number of dollars, and re-deriving it from a schedule that has since been
 * amended would quietly change a debt somebody has already been billed for. The schedule's date is
 * kept alongside so the working can be reconstructed when a carrier argues. The permit is optional,
 * because the citations that matter most are the ones written for having no permit at all.
 */
@Entity
@Table(name = "citations")
public class CitationRow {

    @Id
    @Column(name = "number", length = CODE_LENGTH, nullable = false)
    private String number;

    @Column(name = "written", nullable = false)
    private LocalDate written;

    @Column(name = "filed", nullable = false)
    private LocalDate filed;

    @Column(name = "officer", length = NAME_LENGTH, nullable = false)
    private String officer;

    @Column(name = "usdot", nullable = false)
    private int usdot;

    @Column(name = "carrier_name", length = NAME_LENGTH, nullable = false)
    private String carrierName;

    @Column(name = "offence", length = 32, nullable = false)
    private Offence offence;

    @Column(name = "place", length = NAME_LENGTH, nullable = false)
    private String place;

    @Column(name = "permit", length = CODE_LENGTH)
    private String permit;

    @Column(name = "over")
    private Pounds over;

    @Column(name = "base")
    private Dollars base;

    @Column(name = "multiplier", nullable = false)
    private int multiplier = 1;

    @Column(name = "amount")
    private Dollars amount;

    @Column(name = "schedule", nullable = false)
    private LocalDate schedule;

    @Column(name = "state", length = 32, nullable = false)
    private CitationState state = CitationState.WRITTEN;

    @Column(name = "closing_reason", length = TEXT_LENGTH, nullable = false)
    private String closingReason = "";

    @Column(name = "note", length = TEXT_LENGTH, nullable = false)
    private String note = "";

    protected CitationRow() {}

    /**
     * The row for a citation, assessed under the penalty schedule of a day.
     *
     * @param schedule the effective day of the schedule the assessment came from
     */
    public static CitationRow of(Citation citation, LocalDate schedule) {
        CitationRow row = new CitationRow();
        row.number = citation.number();
        row.written = citation.written();
        row.filed = citation.filed();
        row.officer = citation.officer();
        row.usdot = citation.usdot();
        row.carrierName = citation.carrierName();
        row.offence = citation.offence();
        row.place = citation.place();
        row.permit = citation.permit().orElse(null);
        row.over = citation.over().orElse(null);
        row.base = citation.assessed().base();
        row.multiplier = citation.assessed().multiplier();
        row.amount = citation.assessed().total();
        row.schedule = schedule;
        row.state = citation.state();
        row.note = citation.note();
        return row;
    }

    public String getNumber() {
        return number;
    }

    public LocalDate getWritten() {
        return written;
    }

    public LocalDate getFiled() {
        return filed;
    }

    public String getOfficer() {
        return officer;
    }

    public int getUsdot() {
        return usdot;
    }

    public String getCarrierName() {
        return carrierName;
    }

    public Offence getOffence() {
        return offence;
    }

    public String getPlace() {
        return place;
    }

    /** The permit the load was stopped under, if there was one. */
    public Optional<String> getPermit() {
        return Optional.ofNullable(permit);
    }

    /** By how much it was over, for an offence measured by weight. */
    public Optional<Pounds> getOver() {
        return Optional.ofNullable(over);
    }

    public Dollars getBase() {
        return base;
    }

    public int getMultiplier() {
        return multiplier;
    }

    public Dollars getAmount() {
        return amount;
    }

    public LocalDate getSchedule() {
        return schedule;
    }

    public CitationState getState() {
        return state;
    }

    public void setState(CitationState state) {
        this.state = state;
    }

    public String getClosingReason() {
        return closingReason;
    }

    public void setClosingReason(String closingReason) {
        this.closingReason = closingReason;
    }

    public String getNote() {
        return note;
    }

    public Citation toDomain() {
        return Citation.builder()
                .number(number)
                .written(written)
                .filed(filed)
                .officer(officer)
                .usdot(usdot)
                .carrierName(carrierName)
                .offence(offence)
                .place(place)
                .assessed(new Assessed(offence, base, multiplier, amount, Optional.ofNullable(over)))
                .permit(permit)
                .over(over)
                .note(note == null ? "" : note)
                .state(state)
                .build();
    }

    @Override
    public String toString() {
        return "<CitationRow " + number + ">";
    }
}
