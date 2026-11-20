package io.github.rkaushikdev.jeepdolly.persistence.models;

import static io.github.rkaushikdev.jeepdolly.persistence.Columns.CODE_LENGTH;
import static io.github.rkaushikdev.jeepdolly.persistence.Columns.NAME_LENGTH;
import static io.github.rkaushikdev.jeepdolly.persistence.Columns.TEXT_LENGTH;

import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.Miles;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.permits.PermitState;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * One issued permit, complete on its own.
 *
 * <p>A permit row copies rather than joins. It holds the printed narrative, the rendered condition
 * text and the fee lines as words and numbers, not as references to the library and the schedule
 * they came from, which is deliberate duplication and the whole reason the office can answer a
 * question about a permit written four years ago without keeping four years of tables identical to
 * what they were.
 */
@Entity
@Table(
        name = "permits",
        indexes = {
            @Index(name = "ix_permits_application", columnList = "application"),
            @Index(name = "ix_permits_issued", columnList = "issued"),
            @Index(name = "ix_permits_series", columnList = "series"),
            @Index(name = "ix_permits_state", columnList = "state"),
            @Index(name = "ix_permits_usdot", columnList = "usdot"),
            @Index(name = "ix_permits_valid_from", columnList = "valid_from"),
            @Index(name = "ix_permits_valid_to", columnList = "valid_to")
        })
public class PermitRow {

    @Id
    @Column(name = "number", length = CODE_LENGTH, nullable = false)
    private String number;

    @Column(name = "series", length = 2, nullable = false)
    private String series;

    @Column(name = "application", length = CODE_LENGTH, nullable = false)
    private String application;

    @Column(name = "usdot", nullable = false)
    private int usdot;

    @Column(name = "carrier_name", length = NAME_LENGTH, nullable = false)
    private String carrierName;

    @Column(name = "issued", nullable = false)
    private LocalDate issued;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to", nullable = false)
    private LocalDate validTo;

    @Column(name = "origin", length = CODE_LENGTH, nullable = false)
    private String origin;

    @Column(name = "destination", length = CODE_LENGTH, nullable = false)
    private String destination;

    @Column(name = "route", length = TEXT_LENGTH, nullable = false)
    private String route;

    @Column(name = "narrative", length = TEXT_LENGTH, nullable = false)
    private String narrative;

    @Column(name = "gross")
    private Pounds gross;

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

    @Column(name = "distance")
    private Miles distance;

    @Column(name = "state", length = 32, nullable = false)
    private PermitState state = PermitState.ISSUED;

    @Column(name = "void_reason", length = TEXT_LENGTH, nullable = false)
    private String voidReason = "";

    @Column(name = "superseded_by", length = CODE_LENGTH)
    private String supersededBy;

    @Column(name = "supersedes", length = CODE_LENGTH)
    private String supersedes;

    @Column(name = "schedule", length = NAME_LENGTH, nullable = false)
    private String schedule = "";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "application",
            nullable = false,
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_permits_application_applications"))
    private ApplicationRow applicationRow;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "usdot",
            nullable = false,
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_permits_usdot_carriers"))
    private CarrierRow carrier;

    @OneToMany(mappedBy = "permit", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<PermitConditionRow> conditions = new ArrayList<>();

    @OneToMany(mappedBy = "permit", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<FeeLineRow> fees = new ArrayList<>();

    protected PermitRow() {}

    /** A permit with nothing on it but its number; the rest is set column by column. */
    public PermitRow(String number) {
        this.number = number;
    }

    public String getNumber() {
        return number;
    }

    public String getSeries() {
        return series;
    }

    public void setSeries(String series) {
        this.series = series;
    }

    public String getApplication() {
        return application;
    }

    public void setApplication(String application) {
        this.application = application;
    }

    public int getUsdot() {
        return usdot;
    }

    public void setUsdot(int usdot) {
        this.usdot = usdot;
    }

    public String getCarrierName() {
        return carrierName;
    }

    public void setCarrierName(String carrierName) {
        this.carrierName = carrierName;
    }

    public LocalDate getIssued() {
        return issued;
    }

    public void setIssued(LocalDate issued) {
        this.issued = issued;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDate validFrom) {
        this.validFrom = validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    public void setValidTo(LocalDate validTo) {
        this.validTo = validTo;
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

    public String getRoute() {
        return route;
    }

    public void setRoute(String route) {
        this.route = route;
    }

    public String getNarrative() {
        return narrative;
    }

    public void setNarrative(String narrative) {
        this.narrative = narrative;
    }

    public Pounds getGross() {
        return gross;
    }

    public void setGross(Pounds gross) {
        this.gross = gross;
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

    public Miles getDistance() {
        return distance;
    }

    public void setDistance(Miles distance) {
        this.distance = distance;
    }

    public PermitState getState() {
        return state;
    }

    public void setState(PermitState state) {
        this.state = state;
    }

    public String getVoidReason() {
        return voidReason;
    }

    public void setVoidReason(String voidReason) {
        this.voidReason = voidReason;
    }

    public Optional<String> getSupersededBy() {
        return Optional.ofNullable(supersededBy);
    }

    /** Null clears it. */
    public void setSupersededBy(String supersededBy) {
        this.supersededBy = supersededBy;
    }

    public Optional<String> getSupersedes() {
        return Optional.ofNullable(supersedes);
    }

    /** Null clears it. */
    public void setSupersedes(String supersedes) {
        this.supersedes = supersedes;
    }

    public String getSchedule() {
        return schedule;
    }

    public void setSchedule(String schedule) {
        this.schedule = schedule;
    }

    /** The route as segment idents, in driven order. */
    public List<String> getRouteIdents() {
        if (route == null) {
            return List.of();
        }
        return Arrays.stream(route.split(",", -1)).filter(one -> !one.isEmpty()).toList();
    }

    /** How many days it runs, both ends included. */
    public int getDays() {
        return (int) ChronoUnit.DAYS.between(validFrom, validTo) + 1;
    }

    /** The conditions as printed, in order. Read only: add one with {@link #addCondition}. */
    public List<PermitConditionRow> getConditions() {
        return Collections.unmodifiableList(conditions);
    }

    /** The fee lines as charged, in order. Read only: add one with {@link #addFee}. */
    public List<FeeLineRow> getFees() {
        return Collections.unmodifiableList(fees);
    }

    public PermitConditionRow addCondition(PermitConditionRow condition) {
        condition.belongTo(this);
        conditions.add(condition);
        return condition;
    }

    public FeeLineRow addFee(FeeLineRow fee) {
        fee.belongTo(this);
        fees.add(fee);
        return fee;
    }

    @Override
    public String toString() {
        return "<PermitRow " + number + " " + state + ">";
    }
}
