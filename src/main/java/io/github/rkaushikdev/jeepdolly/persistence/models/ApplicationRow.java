package io.github.rkaushikdev.jeepdolly.persistence.models;

import static io.github.rkaushikdev.jeepdolly.persistence.Columns.CODE_LENGTH;
import static io.github.rkaushikdev.jeepdolly.persistence.Columns.NAME_LENGTH;
import static io.github.rkaushikdev.jeepdolly.persistence.Columns.TEXT_LENGTH;

import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.permits.ApplicationState;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * One request for one permit, exactly as it was asked for.
 *
 * <p>An application is four tables because a combination is a list of things, and a list in a text
 * column is a list nobody can query; the office does ask how many nine axle trailers came through
 * last year. The children carry a position rather than leaning on insertion order, and the spacing
 * on an axle row is the gap to the axle in front of it, so only the first axle has none.
 *
 * <p>Nothing here validates. The domain objects do that, and the row is allowed to hold something
 * the domain would refuse, because an application that was typed wrong still has to be stored,
 * quoted back and refused in writing.
 */
@Entity
@Table(name = "applications")
public class ApplicationRow {

    @Id
    @Column(name = "reference", length = CODE_LENGTH, nullable = false)
    private String reference;

    @Column(name = "usdot", nullable = false)
    private int usdot;

    @Column(name = "commodity", length = CODE_LENGTH, nullable = false)
    private String commodity;

    @Column(name = "origin", length = CODE_LENGTH, nullable = false)
    private String origin;

    @Column(name = "destination", length = CODE_LENGTH, nullable = false)
    private String destination;

    @Column(name = "travel_start", nullable = false)
    private LocalDate travelStart;

    @Column(name = "travel_end", nullable = false)
    private LocalDate travelEnd;

    @Column(name = "filed", nullable = false)
    private LocalDate filed;

    @Column(name = "state", length = 32, nullable = false)
    private ApplicationState state = ApplicationState.DRAFT;

    @Column(name = "applicant_name", length = NAME_LENGTH, nullable = false)
    private String applicantName;

    @Column(name = "applicant_phone", length = CODE_LENGTH, nullable = false)
    private String applicantPhone;

    @Column(name = "applicant_email", length = NAME_LENGTH, nullable = false)
    private String applicantEmail = "";

    @Column(name = "route", length = TEXT_LENGTH, nullable = false)
    private String route = "";

    @Column(name = "escort_cards", length = TEXT_LENGTH, nullable = false)
    private String escortCards = "";

    @Column(name = "notes", length = TEXT_LENGTH, nullable = false)
    private String notes = "";

    @Column(name = "width")
    private Inches width;

    @Column(name = "height")
    private Inches height;

    @Column(name = "length")
    private Inches length;

    @Column(name = "front_overhang")
    private Inches frontOverhang;

    @Column(name = "rear_overhang")
    private Inches rearOverhang;

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<ApplicationAxleRow> axles = new ArrayList<>();

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<ApplicationUnitRow> units = new ArrayList<>();

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<ApplicationPieceRow> pieces = new ArrayList<>();

    protected ApplicationRow() {}

    /** An application with nothing on it but its reference; the rest is set column by column. */
    public ApplicationRow(String reference) {
        this.reference = reference;
    }

    public String getReference() {
        return reference;
    }

    public int getUsdot() {
        return usdot;
    }

    public void setUsdot(int usdot) {
        this.usdot = usdot;
    }

    public String getCommodity() {
        return commodity;
    }

    public void setCommodity(String commodity) {
        this.commodity = commodity;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public LocalDate getTravelStart() {
        return travelStart;
    }

    public void setTravelStart(LocalDate travelStart) {
        this.travelStart = travelStart;
    }

    public LocalDate getTravelEnd() {
        return travelEnd;
    }

    public void setTravelEnd(LocalDate travelEnd) {
        this.travelEnd = travelEnd;
    }

    public LocalDate getFiled() {
        return filed;
    }

    public void setFiled(LocalDate filed) {
        this.filed = filed;
    }

    public ApplicationState getState() {
        return state;
    }

    public void setState(ApplicationState state) {
        this.state = state;
    }

    public String getApplicantName() {
        return applicantName;
    }

    public void setApplicantName(String applicantName) {
        this.applicantName = applicantName;
    }

    public String getApplicantPhone() {
        return applicantPhone;
    }

    public void setApplicantPhone(String applicantPhone) {
        this.applicantPhone = applicantPhone;
    }

    public String getApplicantEmail() {
        return applicantEmail;
    }

    public void setApplicantEmail(String applicantEmail) {
        this.applicantEmail = applicantEmail;
    }

    public String getRoute() {
        return route;
    }

    public void setRoute(String route) {
        this.route = route;
    }

    public String getEscortCards() {
        return escortCards;
    }

    public void setEscortCards(String escortCards) {
        this.escortCards = escortCards;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Inches getWidth() {
        return width;
    }

    public void setWidth(Inches width) {
        this.width = width;
    }

    public Inches getHeight() {
        return height;
    }

    public void setHeight(Inches height) {
        this.height = height;
    }

    public Inches getLength() {
        return length;
    }

    public void setLength(Inches length) {
        this.length = length;
    }

    public Inches getFrontOverhang() {
        return frontOverhang;
    }

    public void setFrontOverhang(Inches frontOverhang) {
        this.frontOverhang = frontOverhang;
    }

    public Inches getRearOverhang() {
        return rearOverhang;
    }

    public void setRearOverhang(Inches rearOverhang) {
        this.rearOverhang = rearOverhang;
    }

    /** The written route as segment idents, in order. */
    public List<String> getRouteIdents() {
        return split(route);
    }

    /**
     * The pilot car cards named, in the order the hauler wrote them. Order is kept because the
     * first card named is the one the counter telephones.
     */
    public List<String> getCards() {
        return split(escortCards);
    }

    private static List<String> split(String joined) {
        if (joined == null) {
            return List.of();
        }
        return Arrays.stream(joined.split(",", -1))
                .filter(one -> !one.isEmpty())
                .toList();
    }

    /** The axles, in road order. Read only: add one with {@link #addAxle}. */
    public List<ApplicationAxleRow> getAxles() {
        return Collections.unmodifiableList(axles);
    }

    /** The equipment, front to back. Read only: add one with {@link #addUnit}. */
    public List<ApplicationUnitRow> getUnits() {
        return Collections.unmodifiableList(units);
    }

    /** The pieces on the trailer. Read only: add one with {@link #addPiece}. */
    public List<ApplicationPieceRow> getPieces() {
        return Collections.unmodifiableList(pieces);
    }

    public int getAxleCount() {
        return axles.size();
    }

    public ApplicationAxleRow addAxle(ApplicationAxleRow axle) {
        axle.belongTo(this);
        axles.add(axle);
        return axle;
    }

    public ApplicationUnitRow addUnit(ApplicationUnitRow unit) {
        unit.belongTo(this);
        units.add(unit);
        return unit;
    }

    public ApplicationPieceRow addPiece(ApplicationPieceRow piece) {
        piece.belongTo(this);
        pieces.add(piece);
        return piece;
    }

    @Override
    public String toString() {
        return "<ApplicationRow " + reference + " " + state + ">";
    }
}
