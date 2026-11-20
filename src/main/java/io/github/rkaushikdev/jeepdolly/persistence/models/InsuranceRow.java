package io.github.rkaushikdev.jeepdolly.persistence.models;

import static io.github.rkaushikdev.jeepdolly.persistence.Columns.CODE_LENGTH;
import static io.github.rkaushikdev.jeepdolly.persistence.Columns.NAME_LENGTH;

import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.domain.Insurance;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Optional;

/** One certificate of insurance, as filed. Never edited: a renewal is a new row. */
@Entity
@Table(name = "insurance_certificates")
public class InsuranceRow {

    @Id
    @Column(name = "policy", length = CODE_LENGTH, nullable = false)
    private String policy;

    @Column(name = "usdot", nullable = false)
    private int usdot;

    @Column(name = "insurer", length = NAME_LENGTH, nullable = false)
    private String insurer;

    @Column(name = "liability")
    private Dollars liability;

    @Column(name = "effective", nullable = false)
    private LocalDate effective;

    @Column(name = "expires", nullable = false)
    private LocalDate expires;

    @Column(name = "filed")
    private LocalDate filed;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usdot", insertable = false, updatable = false)
    private CarrierRow carrier;

    protected InsuranceRow() {}

    public InsuranceRow(
            String policy, int usdot, String insurer, Dollars liability, LocalDate effective, LocalDate expires) {
        this.policy = policy;
        this.usdot = usdot;
        this.insurer = insurer;
        this.liability = liability;
        this.effective = effective;
        this.expires = expires;
    }

    /** The row for a certificate filed against a carrier, with no day of filing written down. */
    public static InsuranceRow of(int usdot, Insurance cover) {
        return new InsuranceRow(
                cover.policy(), usdot, cover.insurer(), cover.liability(), cover.effective(), cover.expires());
    }

    /** The row for a certificate filed against a carrier on a day. */
    public static InsuranceRow of(int usdot, Insurance cover, LocalDate filed) {
        InsuranceRow row = of(usdot, cover);
        row.filed = filed;
        return row;
    }

    void belongTo(CarrierRow owner) {
        this.carrier = owner;
        this.usdot = owner.getUsdot();
    }

    public String getPolicy() {
        return policy;
    }

    public int getUsdot() {
        return usdot;
    }

    public String getInsurer() {
        return insurer;
    }

    public Dollars getLiability() {
        return liability;
    }

    public LocalDate getEffective() {
        return effective;
    }

    public LocalDate getExpires() {
        return expires;
    }

    /** The day the office received it, when somebody wrote that down. */
    public Optional<LocalDate> getFiled() {
        return Optional.ofNullable(filed);
    }

    public Insurance toDomain() {
        return new Insurance(policy, insurer, liability, effective, expires);
    }

    @Override
    public String toString() {
        return "<InsuranceRow " + policy + ">";
    }
}
