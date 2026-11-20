package io.github.rkaushikdev.jeepdolly.persistence.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.Miles;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.domain.CommodityBook;
import io.github.rkaushikdev.jeepdolly.permits.PermitState;
import io.github.rkaushikdev.jeepdolly.persistence.IntegrityViolation;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.support.Carriers;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The permit tables, which copy the words rather than pointing at them. */
class PermitModelsTest {

    private static final LocalDate ISSUED = LocalDate.of(2025, 4, 7);
    private static final LocalDate START = LocalDate.of(2025, 4, 8);
    private static final LocalDate END = LocalDate.of(2025, 4, 10);

    private UnitOfWork session;

    @BeforeEach
    void openWithTheApplicationTheyAnswer() {
        session = Databases.empty().unitOfWork();
        session.add(CommodityRow.of(CommodityBook.standard().get("EXCV")));
        session.add(CarrierRow.of(Carriers.aCarrierRegister().get(1204471)));
        ApplicationRow application = new ApplicationRow("25-04471");
        application.setUsdot(1204471);
        application.setCommodity("EXCV");
        application.setOrigin("waterville");
        application.setDestination("pittsfield");
        application.setTravelStart(START);
        application.setTravelEnd(END);
        application.setFiled(ISSUED);
        application.setApplicantName("Dana Bickford");
        application.setApplicantPhone("207 555 0143");
        application.setWidth(Inches.of(12));
        application.setHeight(Inches.of(13, 6));
        application.setLength(Inches.of(82));
        application.setFrontOverhang(new Inches(0));
        application.setRearOverhang(Inches.of(6));
        session.add(application);
        session.commit();
    }

    @AfterEach
    void close() {
        session.close();
    }

    /** The excavator permit as it sits on the row, before any line is added. */
    private static PermitRow aPermitRow(String number) {
        PermitRow row = new PermitRow(number);
        row.setSeries("P");
        row.setApplication("25-04471");
        row.setUsdot(1204471);
        row.setCarrierName("Bickford Heavy Haul");
        row.setIssued(ISSUED);
        row.setValidFrom(START);
        row.setValidTo(END);
        row.setOrigin("waterville");
        row.setDestination("pittsfield");
        row.setRoute("US201-1,US201-2,SA148-2,SR43-1");
        row.setNarrative("from Waterville, US 201 to Skowhegan");
        row.setGross(new Pounds(94000));
        row.setWidth(Inches.of(12));
        row.setHeight(Inches.of(13, 6));
        row.setLength(Inches.of(82));
        row.setFrontOverhang(new Inches(0));
        row.setRearOverhang(Inches.of(6));
        row.setDistance(Miles.of(35, 20));
        row.setSchedule("schedule of 2024-01-01");
        return row;
    }

    private PermitRow filled() {
        PermitRow row = aPermitRow("25-P-004471");
        row.addCondition(new PermitConditionRow(0, "T-01", "travel", "Valid from 2025-04-08."));
        row.addCondition(new PermitConditionRow(1, "R-01", "route", "Route as written."));
        row.addFee(new FeeLineRow(0, "ordinary permit", Dollars.of(40)));
        row.addFee(new FeeLineRow(1, "34 miles on arterial", Dollars.of(6, 12)));
        session.add(row);
        session.commit();
        return row;
    }

    private PermitRow permit(String number) {
        return session.entityManager().find(PermitRow.class, number);
    }

    @Test
    void aPermitAndItsLinesGoInTogether() {
        filled();
        session.entityManager().clear();
        PermitRow row = permit("25-P-004471");
        assertThat(row.getState()).isSameAs(PermitState.ISSUED);
        assertThat(row.getConditions()).hasSize(2);
        assertThat(row.getFees()).hasSize(2);
        assertThat(row.getDays()).isEqualTo(3);
        assertThat(row).hasToString("<PermitRow 25-P-004471 issued>");
    }

    @Test
    void theConditionsKeepTheWordsThatWerePrinted() {
        filled();
        PermitRow row = permit("25-P-004471");
        assertThat(row.getConditions().get(0).getBody()).isEqualTo("Valid from 2025-04-08.");
        assertThat(row.getConditions()).extracting(PermitConditionRow::getCode).containsExactly("T-01", "R-01");
    }

    @Test
    void theFeeLinesAddBackToWhatWasTaken() {
        filled();
        PermitRow row = permit("25-P-004471");
        long total =
                row.getFees().stream().mapToLong(one -> one.getAmount().cents()).sum();
        assertThat(total).isEqualTo(4612);
        assertThat(row.getFees().get(1).getDescription()).isEqualTo("34 miles on arterial");
    }

    @Test
    void theRouteComesBackAsIdentsInDrivenOrder() {
        filled();
        assertThat(permit("25-P-004471").getRouteIdents()).containsExactly("US201-1", "US201-2", "SA148-2", "SR43-1");
    }

    @Test
    void aPermitWithNoRouteIsRefusedByTheDatabase() {
        PermitRow nowhere = aPermitRow("25-P-000002");
        nowhere.setRoute("");
        session.add(nowhere);
        assertThatThrownBy(session::commit).isInstanceOf(IntegrityViolation.class);
    }

    @Test
    void aPermitValidBeforeItWasIssuedIsRefusedByTheDatabase() {
        PermitRow early = aPermitRow("25-P-000003");
        early.setValidFrom(LocalDate.of(2025, 4, 1));
        session.add(early);
        assertThatThrownBy(session::commit).isInstanceOf(IntegrityViolation.class);
    }

    @Test
    void aPermitThatEndsBeforeItStartsIsRefusedByTheDatabase() {
        PermitRow backwards = aPermitRow("25-P-000004");
        backwards.setValidFrom(END);
        backwards.setValidTo(START);
        session.add(backwards);
        assertThatThrownBy(session::commit).isInstanceOf(IntegrityViolation.class);
    }

    @Test
    void aPermitForAnApplicationNobodyFiledIsRefused() {
        PermitRow stranger = aPermitRow("25-P-000005");
        stranger.setApplication("25-99999");
        session.add(stranger);
        assertThatThrownBy(session::commit).isInstanceOf(IntegrityViolation.class);
    }

    @Test
    void aConditionWithNoWordsIsRefusedByTheDatabase() {
        PermitRow row = filled();
        row.getConditions().get(0).setBody("");
        assertThatThrownBy(session::commit).isInstanceOf(IntegrityViolation.class);
    }

    @Test
    void voidingIsAnEditToTheRowAndTheLinesStay() {
        filled();
        PermitRow row = permit("25-P-004471");
        row.setState(PermitState.VOID);
        row.setVoidReason("load cancelled");
        session.commit();
        assertThat(permit("25-P-004471").getState()).isSameAs(PermitState.VOID);
        assertThat(session.entityManager()
                        .createQuery("select c from PermitConditionRow c", PermitConditionRow.class)
                        .getResultList())
                .hasSize(2);
    }

    @Test
    void aReplacementPointsAtWhatItReplacedAndBack() {
        filled();
        PermitRow replacement = aPermitRow("25-R-000001");
        replacement.setSeries("R");
        replacement.setSupersedes("25-P-004471");
        session.add(replacement);
        PermitRow replaced = permit("25-P-004471");
        replaced.setSupersededBy("25-R-000001");
        replaced.setState(PermitState.SUPERSEDED);
        session.commit();
        assertThat(permit("25-R-000001").getSupersedes()).contains("25-P-004471");
        assertThat(permit("25-P-004471").getSupersededBy()).contains("25-R-000001");
    }

    @Test
    void theCounterHoldsOneRowForEachYearAndSeries() {
        session.add(new CounterRow(2025, "P", 4472));
        session.add(new CounterRow(2025, "S", 13));
        session.commit();
        CounterRow row = session.entityManager().find(CounterRow.class, new CounterRow.Key(2025, "P"));
        assertThat(row.getNextSequence()).isEqualTo(4472);
        assertThat(row).hasToString("<CounterRow 2025 P at 4472>");
    }

    @Test
    void aCounterBelowOneIsRefusedByTheDatabase() {
        session.add(new CounterRow(2025, "P", 0));
        assertThatThrownBy(session::commit).isInstanceOf(IntegrityViolation.class);
    }
}
