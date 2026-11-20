package io.github.rkaushikdev.jeepdolly.persistence.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.domain.AxleKind;
import io.github.rkaushikdev.jeepdolly.domain.CarrierRegister;
import io.github.rkaushikdev.jeepdolly.domain.CommodityBook;
import io.github.rkaushikdev.jeepdolly.domain.UnitKind;
import io.github.rkaushikdev.jeepdolly.permits.ApplicationState;
import io.github.rkaushikdev.jeepdolly.persistence.IntegrityViolation;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.support.Carriers;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The application tables, and the children that hold the combination. */
class ApplicationModelsTest {

    private static final LocalDate FILED = LocalDate.of(2025, 4, 7);
    private static final LocalDate START = LocalDate.of(2025, 4, 8);
    private static final LocalDate END = LocalDate.of(2025, 4, 10);

    private UnitOfWork session;

    @BeforeEach
    void openWithCommoditiesAndCarriers() {
        session = Databases.empty().unitOfWork();
        CommodityBook book = CommodityBook.standard();
        for (String code : book.codes()) {
            session.add(CommodityRow.of(book.get(code)));
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

    /** The excavator move as it sits on the row, before any child row is added. */
    private static ApplicationRow anApplicationRow(String reference) {
        ApplicationRow row = new ApplicationRow(reference);
        row.setUsdot(1204471);
        row.setCommodity("EXCV");
        row.setOrigin("waterville");
        row.setDestination("pittsfield");
        row.setTravelStart(START);
        row.setTravelEnd(END);
        row.setFiled(FILED);
        row.setApplicantName("Dana Bickford");
        row.setApplicantPhone("207 555 0143");
        row.setWidth(Inches.of(12));
        row.setHeight(Inches.of(13, 6));
        row.setLength(Inches.of(82));
        row.setFrontOverhang(new Inches(0));
        row.setRearOverhang(Inches.of(6));
        return row;
    }

    /** One axle of the tractor and lowboy, by kind, weight and the gap in front of it. */
    private record Axle(AxleKind kind, int weight, Integer spacing) {}

    private ApplicationRow filled() {
        ApplicationRow row = anApplicationRow("25-04471");
        row.setRoute("US201-1,US201-2,SA148-2,SR43-1");
        List<Axle> axles = List.of(
                new Axle(AxleKind.STEER, 12000, null),
                new Axle(AxleKind.DRIVE, 17000, 200),
                new Axle(AxleKind.DRIVE, 17000, 54),
                new Axle(AxleKind.TRAILER, 16000, 180),
                new Axle(AxleKind.TRAILER, 16000, 52),
                new Axle(AxleKind.TRAILER, 16000, 52));
        for (int position = 0; position < axles.size(); position++) {
            Axle one = axles.get(position);
            boolean steer = one.kind() == AxleKind.STEER;
            row.addAxle(new ApplicationAxleRow(
                    position,
                    one.kind(),
                    new Pounds(one.weight()),
                    steer ? 2 : 4,
                    new Inches(steer ? 16 : 11),
                    false,
                    one.spacing() == null ? null : new Inches(one.spacing())));
        }
        row.addUnit(new ApplicationUnitRow(0, UnitKind.TRACTOR, 3, "ME 4471", new Pounds(19000)));
        row.addUnit(new ApplicationUnitRow(1, UnitKind.LOWBOY, 3, "ME 7712", new Pounds(24000)));
        row.addPiece(new ApplicationPieceRow(0, "EXCV", "Cat 336 excavator", "TMD01297"));
        session.add(row);
        session.commit();
        return row;
    }

    private ApplicationRow application() {
        return session.entityManager().find(ApplicationRow.class, "25-04471");
    }

    private <T> List<T> all(Class<T> type) {
        return session.entityManager()
                .createQuery("select r from " + type.getSimpleName() + " r", type)
                .getResultList();
    }

    @Test
    void anApplicationAndItsChildrenGoInTogether() {
        filled();
        session.entityManager().clear();
        ApplicationRow row = application();
        assertThat(row.getAxleCount()).isEqualTo(6);
        assertThat(row.getUnits()).hasSize(2);
        assertThat(row.getPieces()).hasSize(1);
        assertThat(row.getState()).isSameAs(ApplicationState.DRAFT);
        assertThat(row).hasToString("<ApplicationRow 25-04471 draft>");
    }

    @Test
    void theAxlesComeBackInRoadOrderWhateverTheDatabaseDid() {
        filled();
        session.entityManager().clear();
        ApplicationRow row = application();
        assertThat(row.getAxles()).extracting(ApplicationAxleRow::getPosition).containsExactly(0, 1, 2, 3, 4, 5);
        assertThat(row.getAxles().subList(0, 3))
                .extracting(one -> one.getWeight().value())
                .containsExactly(12000, 17000, 17000);
    }

    @Test
    void theFirstAxleIsTheOnlyOneWithNoSpacingInFrontOfIt() {
        filled();
        ApplicationRow row = application();
        assertThat(row.getAxles().get(0).getSpacingBefore()).isEmpty();
        assertThat(row.getAxles().get(1).getSpacingBefore()).contains(new Inches(200));
        assertThat(row.getAxles().subList(1, 6))
                .allSatisfy(one -> assertThat(one.getSpacingBefore()).isPresent());
    }

    @Test
    void theEnvelopeIsStoredAsWholeInches() {
        filled();
        Object[] read = (Object[]) session.entityManager()
                .createNativeQuery("select width, height from applications")
                .getSingleResult();
        assertThat(read).containsExactly(144, 162);
    }

    @Test
    void theRouteIsReadBackAsAListOfIdents() {
        filled();
        assertThat(application().getRouteIdents()).containsExactly("US201-1", "US201-2", "SA148-2", "SR43-1");
        assertThat(anApplicationRow("25-1").getRouteIdents()).isEmpty();
    }

    @Test
    void aSpanThatEndsBeforeItStartsIsRefusedByTheDatabase() {
        ApplicationRow backwards = anApplicationRow("25-2");
        backwards.setTravelStart(END);
        backwards.setTravelEnd(START);
        session.add(backwards);
        assertThatThrownBy(session::commit).isInstanceOf(IntegrityViolation.class);
    }

    @Test
    void aTripToWhereItStartedIsRefusedByTheDatabase() {
        ApplicationRow nowhere = anApplicationRow("25-3");
        nowhere.setDestination("waterville");
        session.add(nowhere);
        assertThatThrownBy(session::commit).isInstanceOf(IntegrityViolation.class);
    }

    @Test
    void anApplicationForACarrierNobodyRegisteredIsRefused() {
        ApplicationRow stranger = anApplicationRow("25-4");
        stranger.setUsdot(9999999);
        session.add(stranger);
        assertThatThrownBy(session::commit).isInstanceOf(IntegrityViolation.class);
    }

    @Test
    void anApplicationNamingACommodityNobodyFiledIsRefused() {
        ApplicationRow unknown = anApplicationRow("25-5");
        unknown.setCommodity("ZZZZ");
        session.add(unknown);
        assertThatThrownBy(session::commit).isInstanceOf(IntegrityViolation.class);
    }

    @Test
    void anAxleOnAnOddCountOfTiresIsRefusedByTheDatabase() {
        ApplicationRow row = filled();
        row.getAxles().get(0).setTires(3);
        assertThatThrownBy(session::commit).isInstanceOf(IntegrityViolation.class);
    }

    @Test
    void aUnitWithNoAxlesIsRefusedByTheDatabase() {
        ApplicationRow row = filled();
        row.getUnits().get(0).setAxles(0);
        assertThatThrownBy(session::commit).isInstanceOf(IntegrityViolation.class);
    }

    @Test
    void deletingAnApplicationTakesItsChildrenWithIt() {
        filled();
        session.delete(application());
        session.commit();
        assertThat(all(ApplicationAxleRow.class)).isEmpty();
        assertThat(all(ApplicationUnitRow.class)).isEmpty();
        assertThat(all(ApplicationPieceRow.class)).isEmpty();
    }

    @Test
    void theStateMovesAndTheRowIsEditedInPlace() {
        filled();
        application().setState(ApplicationState.SUBMITTED);
        session.commit();
        Object read = session.entityManager()
                .createNativeQuery("select state from applications")
                .getSingleResult();
        assertThat(read).isEqualTo("submitted");
    }
}
