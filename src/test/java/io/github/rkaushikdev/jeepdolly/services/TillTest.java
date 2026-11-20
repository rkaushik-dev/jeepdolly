package io.github.rkaushikdev.jeepdolly.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import io.github.rkaushikdev.jeepdolly.permits.Application;
import io.github.rkaushikdev.jeepdolly.permits.PermitNumber;
import io.github.rkaushikdev.jeepdolly.permits.Series;
import io.github.rkaushikdev.jeepdolly.persistence.Database;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.support.Applications;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import io.github.rkaushikdev.jeepdolly.support.Permits;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Taking money, and proving at the end of the day that it adds up. */
class TillTest {

    private static final LocalDate MONDAY = LocalDate.of(2025, 4, 7);
    private static final LocalDate TUESDAY = LocalDate.of(2025, 4, 8);
    private static final String CLERK = "D Bickford";
    private static final String FIRST = "25-P-004471";

    private UnitOfWork work;
    private EntityManager db;

    @BeforeEach
    void issued() {
        Database database = Databases.seeded();
        Application application = Applications.anApplication();
        try (UnitOfWork setup = database.unitOfWork()) {
            setup.applications().add(application);
            setup.commit();
            setup.permits().add(Permits.aPermit(application));
            setup.permits()
                    .add(Permits.permit(application)
                            .number(new PermitNumber(2025, Series.SINGLE, 4472))
                            .build());
            setup.commit();
        }
        work = database.unitOfWork();
        db = work.entityManager();
    }

    @AfterEach
    void close() {
        work.close();
    }

    @Test
    void aPermitWithNothingAgainstItIsOwedInFull() {
        Till.Owing found = Till.owingOn(db, FIRST);
        assertThat(found.charged()).isEqualTo(Dollars.of(46, 12));
        assertThat(found.paid()).isEqualTo(new Dollars(0));
        assertThat(found.owed()).isEqualTo(Dollars.of(46, 12));
        assertThat(found.settled()).isFalse();
    }

    @Test
    void aReceiptSettlesIt() {
        Till.take(db, FIRST, Dollars.of(46, 12), "cash", CLERK, MONDAY);
        work.commit();
        Till.Owing found = Till.owingOn(db, FIRST);
        assertThat(found.settled()).isTrue();
        assertThat(found.toString()).startsWith("25-P-004471  Bickford Heavy Haul");
    }

    @Test
    void aPartPaymentLeavesTheRestOwing() {
        Till.take(db, FIRST, Dollars.of(20), "cash", CLERK, MONDAY);
        work.commit();
        assertThat(Till.owingOn(db, FIRST).owed()).isEqualTo(Dollars.of(26, 12));
    }

    @Test
    void aPaymentTakenTwiceIsPutRightWithARefund() {
        Till.take(db, FIRST, Dollars.of(46, 12), "cash", CLERK, MONDAY);
        Till.take(db, FIRST, Dollars.of(46, 12), "cash", CLERK, MONDAY);
        work.commit();
        assertThat(Till.owingOn(db, FIRST).owed()).isEqualTo(Dollars.of(-46, -12));
        Till.take(db, FIRST, Dollars.of(-46, -12), "refund", CLERK, TUESDAY);
        work.commit();
        assertThat(Till.owingOn(db, FIRST).settled()).isTrue();
    }

    @Test
    void aWayOfPayingTheOfficeDoesNotTakeIsRefused() {
        Impossible refusal = catchThrowableOfType(
                Impossible.class, () -> Till.take(db, FIRST, Dollars.of(40), "goats", CLERK, MONDAY));
        assertThat(refusal.facts().get("method")).isEqualTo("goats");
    }

    @Test
    void aReceiptForNothingIsRefused() {
        assertThatThrownBy(() -> Till.take(db, FIRST, new Dollars(0), "cash", CLERK, MONDAY))
                .isInstanceOf(Impossible.class);
    }

    @Test
    void aReceiptAgainstAPermitNobodyIssuedIsRefused() {
        assertThatThrownBy(() -> Till.take(db, "25-P-999999", Dollars.of(40), "cash", CLERK, MONDAY))
                .isInstanceOf(NotOnFile.class);
    }

    @Test
    void theDrawerHoldsCashAndChequesAndNotTheAccountWork() {
        Till.take(db, FIRST, Dollars.of(46, 12), "cash", CLERK, MONDAY);
        Till.take(db, "25-P-004472", Dollars.of(46, 12), "account", CLERK, MONDAY);
        work.commit();
        Till.Drawer found = Till.drawer(db, MONDAY);
        assertThat(found.inTheDrawer()).isEqualTo(Dollars.of(46, 12));
        assertThat(found.onAccount()).isEqualTo(Dollars.of(46, 12));
        assertThat(found.taken()).isEqualTo(Dollars.of(92, 24));
        assertThat(found.receipts()).isEqualTo(2);
    }

    @Test
    void theDrawerIsCountedByDay() {
        Till.take(db, FIRST, Dollars.of(46, 12), "cash", CLERK, MONDAY);
        work.commit();
        assertThat(Till.drawer(db, TUESDAY).receipts()).isZero();
        assertThat(Till.drawer(db, MONDAY).lines().get(1)).isEqualTo("in the drawer  $46.12");
    }

    @Test
    void whatIsOutstandingIsOldestFirstAndLosesWhatIsPaid() {
        assertThat(Till.outstanding(db, TUESDAY)).hasSize(2);
        Till.take(db, FIRST, Dollars.of(46, 12), "cheque", CLERK, MONDAY);
        work.commit();
        assertThat(Till.outstanding(db, TUESDAY)).extracting(Till.Owing::permit).containsExactly("25-P-004472");
    }

    @Test
    void aCarrierIsToldOneNumber() {
        assertThat(Till.owedByCarrier(db, 1204471, TUESDAY)).isEqualTo(Dollars.of(92, 24));
        Till.take(db, FIRST, Dollars.of(46, 12), "cash", CLERK, MONDAY);
        work.commit();
        assertThat(Till.owedByCarrier(db, 1204471, TUESDAY)).isEqualTo(Dollars.of(46, 12));
        assertThat(Till.owedByCarrier(db, 2299001, TUESDAY)).isEqualTo(new Dollars(0));
    }
}
