package io.github.rkaushikdev.jeepdolly.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.conditions.Condition;
import io.github.rkaushikdev.jeepdolly.domain.CarrierRegister;
import io.github.rkaushikdev.jeepdolly.domain.CommodityBook;
import io.github.rkaushikdev.jeepdolly.permits.Application;
import io.github.rkaushikdev.jeepdolly.permits.Permit;
import io.github.rkaushikdev.jeepdolly.permits.PermitNumber;
import io.github.rkaushikdev.jeepdolly.permits.PermitState;
import io.github.rkaushikdev.jeepdolly.permits.Series;
import io.github.rkaushikdev.jeepdolly.persistence.models.ApplicationRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.CarrierRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.CommodityRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.PermitRow;
import io.github.rkaushikdev.jeepdolly.support.Applications;
import io.github.rkaushikdev.jeepdolly.support.Carriers;
import io.github.rkaushikdev.jeepdolly.support.Combinations;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import io.github.rkaushikdev.jeepdolly.support.Permits;
import io.github.rkaushikdev.jeepdolly.support.Streams;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Applications and permits out to rows and back, exactly. */
class MappersTest {

    private static final CommodityBook BOOK = CommodityBook.standard();
    private static final LocalDate ISSUED = LocalDate.of(2025, 4, 7);

    private UnitOfWork session;

    @BeforeEach
    void openWithCommoditiesAndCarriers() {
        session = Databases.empty().unitOfWork();
        for (String code : BOOK.codes()) {
            session.add(CommodityRow.of(BOOK.get(code)));
        }
        CarrierRegister register = Carriers.aCarrierRegister();
        for (int usdot : List.of(Carriers.BICKFORD, Carriers.AROOSTOOK)) {
            session.add(CarrierRow.of(register.get(usdot)));
        }
        session.commit();
    }

    @AfterEach
    void close() {
        session.close();
    }

    private ApplicationRow application(String reference) {
        return session.entityManager().find(ApplicationRow.class, reference);
    }

    private PermitRow permit(String number) {
        return session.entityManager().find(PermitRow.class, number);
    }

    private Application filed(Application application) {
        session.add(Mappers.applicationToRow(application));
        session.commit();
        return application;
    }

    @Test
    void anApplicationSurvivesTheRoundTripExactly() {
        Application application = filed(
                Applications.application().route(List.of("US201-1", "US201-2")).build());
        session.entityManager().clear();
        assertThat(Mappers.rowToApplication(application("25-04471"), BOOK)).isEqualTo(application);
    }

    @Test
    void aNineAxleCombinationSurvivesTheRoundTrip() {
        Application application = filed(Applications.application()
                .combination(Combinations.aCombination(Streams.jeepedLowboy()))
                .build());
        session.entityManager().clear();
        Application back = Mappers.rowToApplication(application("25-04471"), BOOK);
        assertThat(back.combination().stream().count()).isEqualTo(9);
        assertThat(back.combination().stream().spacings())
                .isEqualTo(application.combination().stream().spacings());
        assertThat(back).isEqualTo(application);
    }

    @Test
    void thePiecesComeBackPointingAtTheCommodityTable() {
        filed(Applications.anApplication());
        Application back = Mappers.rowToApplication(application("25-04471"), BOOK);
        assertThat(back.pieces().get(0).commodity().code()).isEqualTo("EXCV");
        assertThat(back.pieces().get(0).serial()).contains("TMD01297");
    }

    @Test
    void anApplicationWithNoAxlesOnFileIsRefusedRatherThanGuessed() {
        filed(Applications.anApplication());
        session.entityManager().createQuery("delete from ApplicationAxleRow").executeUpdate();
        session.commit();
        session.entityManager().clear();
        ApplicationRow bare = application("25-04471");
        assertThatThrownBy(() -> Mappers.rowToApplication(bare, BOOK)).isInstanceOf(Impossible.class);
    }

    @Test
    void theStateOfAnApplicationGoesOutAndComesBack() {
        Application application = Applications.anApplication();
        application.submit(ISSUED);
        filed(application);
        Application back = Mappers.rowToApplication(application("25-04471"), BOOK);
        assertThat(back.state()).isSameAs(application.state());
    }

    @Test
    void aPermitSurvivesTheRoundTripExactly() {
        Application application = filed(Applications.anApplication());
        Permit permit = Permits.aPermit(application);
        session.add(Mappers.permitToRow(permit));
        session.commit();
        session.entityManager().clear();
        assertThat(Mappers.rowToPermit(permit("25-P-004471"))).isEqualTo(permit);
    }

    @Test
    void theConditionsComeBackInTheOrderTheyWerePrinted() {
        Application application = filed(Applications.anApplication());
        session.add(Mappers.permitToRow(Permits.aPermit(application)));
        session.commit();
        Permit back = Mappers.rowToPermit(permit("25-P-004471"));
        assertThat(back.conditions()).extracting(Condition::code).containsExactly("R-01", "G-01");
        assertThat(back.conditions().get(0).text()).startsWith("Route as written");
    }

    @Test
    void theFeeLinesComeBackAsMoneyAndAddUp() {
        Application application = filed(Applications.anApplication());
        session.add(Mappers.permitToRow(Permits.aPermit(application)));
        session.commit();
        Permit back = Mappers.rowToPermit(permit("25-P-004471"));
        assertThat(back.quote().total()).isEqualTo(Dollars.of(46, 12));
        assertThat(back.quote().schedule()).isEqualTo("schedule of 2024-01-01");
    }

    @Test
    void aVoidedPermitKeepsItsReasonThroughTheRoundTrip() {
        Application application = filed(Applications.anApplication());
        Permit permit = Permits.aPermit(application);
        permit.makeVoid(application.start(), "load cancelled by the customer");
        session.add(Mappers.permitToRow(permit));
        session.commit();
        Permit back = Mappers.rowToPermit(permit("25-P-004471"));
        assertThat(back.state()).isSameAs(PermitState.VOID);
        assertThat(back.voidReason()).isEqualTo("load cancelled by the customer");
    }

    @Test
    void aReplacementKeepsBothEndsOfTheChain() {
        Application application = filed(Applications.anApplication());
        Permit replaced = Permits.aPermit(application);
        Permit replacement = Permits.permit(application)
                .number(new PermitNumber(2025, Series.REPLACEMENT, 1))
                .supersedes(replaced.number())
                .build();
        replaced.supersede(application.start(), replacement.number());
        session.add(Mappers.permitToRow(replaced));
        session.add(Mappers.permitToRow(replacement));
        session.commit();
        assertThat(Mappers.rowToPermit(permit("25-P-004471")).supersededBy()).contains(replacement.number());
        assertThat(Mappers.rowToPermit(permit("25-R-000001")).supersedes()).contains(replaced.number());
    }

    @Test
    void thePermitSeriesIsStoredSoItCanBeCounted() {
        Application application = filed(Applications.anApplication());
        session.add(Mappers.permitToRow(Permits.aPermit(application)));
        session.commit();
        List<PermitRow> rows = session.entityManager()
                .createQuery("select p from PermitRow p where p.series = 'P'", PermitRow.class)
                .getResultList();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getGross()).isEqualTo(new Pounds(94000));
    }
}
