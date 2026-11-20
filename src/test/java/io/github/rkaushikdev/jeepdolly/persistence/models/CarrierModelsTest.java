package io.github.rkaushikdev.jeepdolly.persistence.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.domain.Billing;
import io.github.rkaushikdev.jeepdolly.domain.Bond;
import io.github.rkaushikdev.jeepdolly.domain.Carrier;
import io.github.rkaushikdev.jeepdolly.domain.CarrierRegister;
import io.github.rkaushikdev.jeepdolly.domain.Insurance;
import io.github.rkaushikdev.jeepdolly.persistence.IntegrityViolation;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.support.Carriers;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The carrier tables, and the paper that is appended rather than edited. */
class CarrierModelsTest {

    private static final LocalDate MARCH = LocalDate.of(2025, 3, 3);
    private static final LocalDate JULY = LocalDate.of(2025, 7, 1);

    private UnitOfWork session;

    @BeforeEach
    void open() {
        session = Databases.empty().unitOfWork();
    }

    @AfterEach
    void close() {
        session.close();
    }

    private static Insurance cover(String policy, LocalDate effective, LocalDate expires) {
        return new Insurance(policy, "Acadia Mutual", Dollars.of(1000000), effective, expires);
    }

    private static Insurance cover() {
        return cover("CA-88214", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 6, 30));
    }

    private CarrierRegister loaded() {
        CarrierRegister register = Carriers.aCarrierRegister();
        for (int usdot : List.of(Carriers.BICKFORD, Carriers.AROOSTOOK)) {
            Carrier carrier = register.get(usdot);
            CarrierRow row = CarrierRow.of(carrier);
            carrier.insurance().ifPresent(paper -> row.addCertificate(InsuranceRow.of(usdot, paper, MARCH)));
            carrier.bond().ifPresent(bond -> row.addBond(BondRow.of(usdot, bond)));
            session.add(row);
        }
        session.commit();
        return register;
    }

    private CarrierRow carrier(int usdot) {
        return session.entityManager().find(CarrierRow.class, usdot);
    }

    private <T> List<T> all(Class<T> type) {
        return session.entityManager()
                .createQuery("select r from " + type.getSimpleName() + " r", type)
                .getResultList();
    }

    @Test
    void aCarrierAndItsPaperGoInTogether() {
        loaded();
        session.entityManager().clear();
        CarrierRow row = carrier(1204471);
        assertThat(row.getName()).isEqualTo("Bickford Heavy Haul");
        assertThat(row.getCertificates()).hasSize(1);
        assertThat(row.getBonds()).hasSize(1);
        assertThat(row).hasToString("<CarrierRow 1204471>");
    }

    @Test
    void theCarrierComesBackAsADomainObjectForAGivenDay() {
        loaded();
        Carrier carrier = carrier(1204471).toDomain(MARCH);
        assertThat(carrier.inGoodStanding(MARCH, MARCH)).isTrue();
        assertThat(carrier.insurance().orElseThrow().policy()).isEqualTo("CA-88214");
        assertThat(carrier.billing()).isSameAs(Billing.COUNTER);
    }

    @Test
    void aRenewalIsANewRowAndTheOldOneStays() {
        loaded();
        carrier(1204471)
                .addCertificate(InsuranceRow.of(
                        1204471, cover("CA-99001", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 5, 31))));
        session.commit();
        assertThat(carrier(1204471).getCertificates()).hasSize(2);
        assertThat(carrier(1204471).currentInsurance(MARCH).orElseThrow().getPolicy())
                .isEqualTo("CA-88214");
    }

    @Test
    void theCertificateInForceIsTheOneCoveringTheDayAskedAbout() {
        loaded();
        carrier(1204471)
                .addCertificate(InsuranceRow.of(
                        1204471, cover("CA-99001", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))));
        session.commit();
        assertThat(carrier(1204471)
                        .currentInsurance(LocalDate.of(2026, 3, 3))
                        .orElseThrow()
                        .getPolicy())
                .isEqualTo("CA-99001");
        assertThat(carrier(1204471).currentInsurance(LocalDate.of(2027, 1, 1))).isEmpty();
    }

    @Test
    void aCarrierWithNoPaperReportsItTheSameWayTheDomainDoes() {
        loaded();
        Carrier carrier = carrier(2299001).toDomain(MARCH);
        assertThat(carrier.standing(MARCH, MARCH)).contains("no certificate of insurance on file");
    }

    @Test
    void aCertificateThatExpiresBeforeItStartsIsRefused() {
        loaded();
        session.add(new InsuranceRow(
                "CA-BAD",
                1204471,
                "Acadia Mutual",
                Dollars.of(1000000),
                LocalDate.of(2025, 6, 1),
                LocalDate.of(2025, 1, 1)));
        assertThatThrownBy(session::commit).isInstanceOf(IntegrityViolation.class);
    }

    @Test
    void aCertificateForACarrierNobodyRegisteredIsRefused() {
        session.add(InsuranceRow.of(9999999, cover()));
        assertThatThrownBy(session::commit).isInstanceOf(IntegrityViolation.class);
    }

    @Test
    void aUsdotNumberThatIsNotOneIsRefusedByTheDatabase() {
        session.add(new CarrierRow(0, "Nobody", "Nowhere", "ME"));
        assertThatThrownBy(session::commit).isInstanceOf(IntegrityViolation.class);
    }

    @Test
    void aStateThatIsNotTwoLettersIsRefusedByTheDatabase() {
        session.add(new CarrierRow(5, "Nobody", "Nowhere", "Maine"));
        assertThatThrownBy(session::commit).isInstanceOf(IntegrityViolation.class);
    }

    @Test
    void aSuspensionIsEndedRatherThanDeleted() {
        loaded();
        SuspensionRow order = new SuspensionRow(2299001, "no certificate", MARCH);
        order.setOrderedBy("supervisor");
        carrier(2299001).addSuspension(order);
        session.commit();
        Carrier carrier = carrier(2299001).toDomain(MARCH);
        assertThat(carrier.suspensionOn(MARCH)).isPresent();
        carrier(2299001).getSuspensions().get(0).setUntil(LocalDate.of(2025, 3, 20));
        session.commit();
        assertThat(carrier(2299001).toDomain(JULY).suspensionOn(JULY)).isEmpty();
        assertThat(all(SuspensionRow.class)).hasSize(1);
    }

    @Test
    void aSuspensionThatLiftsBeforeItStartsIsRefused() {
        loaded();
        SuspensionRow backwards = new SuspensionRow(1204471, "nothing", JULY);
        backwards.setUntil(LocalDate.of(2025, 1, 1));
        // A suspension's key is an identity, so the row is written as soon as it is added and the
        // database refuses it there rather than at the commit.
        assertThatThrownBy(() -> {
                    session.add(backwards);
                    session.commit();
                })
                .isInstanceOf(IntegrityViolation.class);
    }

    @Test
    void deletingACarrierTakesItsPaperWithIt() {
        loaded();
        session.delete(carrier(1204471));
        session.commit();
        assertThat(all(InsuranceRow.class)).isEmpty();
        assertThat(all(BondRow.class)).isEmpty();
    }

    @Test
    void aBondRoundTripsThroughTheRow() {
        loaded();
        Bond bond = carrier(1204471).getBonds().get(0).toDomain();
        assertThat(bond).isInstanceOf(Bond.class);
        assertThat(bond.amount()).isEqualTo(Dollars.of(25000));
        assertThat(bond.surety()).isEqualTo("Penobscot Surety");
    }
}
