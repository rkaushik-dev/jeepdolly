package io.github.rkaushikdev.jeepdolly.persistence.models;

import static io.github.rkaushikdev.jeepdolly.persistence.Columns.NAME_LENGTH;

import io.github.rkaushikdev.jeepdolly.domain.Billing;
import io.github.rkaushikdev.jeepdolly.domain.Carrier;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * One carrier, edited in place: the name, the town, the billing arrangement.
 *
 * <p>Its paper is appended rather than edited. A renewed certificate is a new row, because the
 * question asked in a dispute is what cover was in force on the day of the move, and the answer
 * has to survive the renewal that came after it. The current certificate is the one whose dates
 * cover the day asked about, and if two do, the one that took effect last. Suspensions are appended
 * too, and ended with a date rather than deleted.
 */
@Entity
@Table(name = "carriers")
public class CarrierRow {

    @Id
    @Column(name = "usdot", nullable = false)
    private int usdot;

    @Column(name = "name", length = NAME_LENGTH, nullable = false)
    private String name;

    @Column(name = "town", length = NAME_LENGTH, nullable = false)
    private String town;

    @Column(name = "state", length = 2, nullable = false)
    private String state;

    @Column(name = "billing", length = 32, nullable = false)
    private Billing billing = Billing.COUNTER;

    @OneToMany(mappedBy = "carrier", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("effective")
    private List<InsuranceRow> certificates = new ArrayList<>();

    @OneToMany(mappedBy = "carrier", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("effective")
    private List<BondRow> bonds = new ArrayList<>();

    @OneToMany(mappedBy = "carrier", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("fromDay")
    private List<SuspensionRow> suspensions = new ArrayList<>();

    protected CarrierRow() {}

    /** A carrier paying at the counter. */
    public CarrierRow(int usdot, String name, String town, String state) {
        this.usdot = usdot;
        this.name = name;
        this.town = town;
        this.state = state;
    }

    /** The row for a carrier record. Its paper is filed separately. */
    public static CarrierRow of(Carrier carrier) {
        CarrierRow row = new CarrierRow(carrier.usdot(), carrier.name(), carrier.town(), carrier.state());
        row.billing = carrier.billing();
        return row;
    }

    public int getUsdot() {
        return usdot;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTown() {
        return town;
    }

    public void setTown(String town) {
        this.town = town;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Billing getBilling() {
        return billing;
    }

    public void setBilling(Billing billing) {
        this.billing = billing;
    }

    /** Every certificate ever filed, oldest first. Read only: file one with {@link #addCertificate}. */
    public List<InsuranceRow> getCertificates() {
        return Collections.unmodifiableList(certificates);
    }

    /** Every bond ever filed, oldest first. Read only: file one with {@link #addBond}. */
    public List<BondRow> getBonds() {
        return Collections.unmodifiableList(bonds);
    }

    /** Every suspension ever ordered, earliest first. Read only: order one with {@link #addSuspension}. */
    public List<SuspensionRow> getSuspensions() {
        return Collections.unmodifiableList(suspensions);
    }

    /** File a certificate against this carrier. It goes in with the carrier's next flush. */
    public InsuranceRow addCertificate(InsuranceRow certificate) {
        certificate.belongTo(this);
        certificates.add(certificate);
        return certificate;
    }

    /** File a bond against this carrier. */
    public BondRow addBond(BondRow bond) {
        bond.belongTo(this);
        bonds.add(bond);
        return bond;
    }

    /** Order a suspension against this carrier. */
    public SuspensionRow addSuspension(SuspensionRow suspension) {
        suspension.belongTo(this);
        suspensions.add(suspension);
        return suspension;
    }

    /** The certificate in force on a day: the latest to take effect of those that cover it. */
    public Optional<InsuranceRow> currentInsurance(LocalDate on) {
        return certificates.stream()
                .filter(one -> !one.getEffective().isAfter(on) && !on.isAfter(one.getExpires()))
                .max(Comparator.comparing(InsuranceRow::getEffective));
    }

    /** The bond in force on a day: the latest to take effect of those that cover it. */
    public Optional<BondRow> currentBond(LocalDate on) {
        return bonds.stream()
                .filter(one -> !one.getEffective().isAfter(on) && !on.isAfter(one.getExpires()))
                .max(Comparator.comparing(BondRow::getEffective));
    }

    /** The carrier as of today, with the paper in force today. */
    public Carrier toDomain() {
        return toDomain(LocalDate.now());
    }

    /** The carrier as of a day, with the paper that was in force then. */
    public Carrier toDomain(LocalDate on) {
        return Carrier.builder(usdot, name, town, state)
                .billing(billing)
                .insurance(currentInsurance(on).map(InsuranceRow::toDomain).orElse(null))
                .bond(currentBond(on).map(BondRow::toDomain).orElse(null))
                .suspensions(suspensions.stream().map(SuspensionRow::toDomain).toList())
                .build();
    }

    @Override
    public String toString() {
        return "<CarrierRow " + usdot + ">";
    }
}
