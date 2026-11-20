package io.github.rkaushikdev.jeepdolly.exports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.Unreadable;
import io.github.rkaushikdev.jeepdolly.permits.Application;
import io.github.rkaushikdev.jeepdolly.permits.Permit;
import io.github.rkaushikdev.jeepdolly.permits.PermitNumber;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.persistence.models.PermitRow;
import io.github.rkaushikdev.jeepdolly.support.Applications;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import io.github.rkaushikdev.jeepdolly.support.Permits;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The federal return, which is eighty columns and rejected on one bad one. */
class FederalReturnTest {

    private static final LocalDate APRIL = LocalDate.of(2025, 4, 1);
    private static final LocalDate LAST = LocalDate.of(2025, 4, 30);

    private UnitOfWork work;
    private EntityManager db;

    @BeforeEach
    void open() {
        work = Databases.seeded().unitOfWork();
        db = work.entityManager();
    }

    @AfterEach
    void close() {
        work.close();
    }

    private static Federal.Detail detail() {
        return detail("25-P-004471", "Bickford Heavy Haul", Dollars.of(46, 12));
    }

    private static Federal.Detail detail(String number, String carrier, Dollars fee) {
        return new Federal.Detail(number, LocalDate.of(2025, 4, 7), 1204471, carrier, "P", 94000, 35, fee);
    }

    private Permit issued() {
        return issued("25-04471", Permits.NUMBER);
    }

    private Permit issued(String reference, PermitNumber number) {
        Application application = Applications.anApplication(reference);
        work.applications().add(application);
        db.flush();
        Permit permit = Permits.permit(application).number(number).build();
        work.permits().add(permit);
        db.flush();
        return permit;
    }

    @Test
    void textIsLeftJustifiedAndBlankFilled() {
        assertThat(Federal.text("ME", 4)).isEqualTo("ME  ");
    }

    @Test
    void textTooLongIsCutOnTheRightRatherThanRefused() {
        assertThat(Federal.text("Bickford Heavy Haulage Company", 10)).isEqualTo("Bickford H");
    }

    @Test
    void aFieldWithNoWidthIsAMistake() {
        assertThatThrownBy(() -> Federal.text("ME", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Federal.number(1, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void numbersAreRightJustifiedAndZeroFilled() {
        assertThat(Federal.number(35, 5)).isEqualTo("00035");
        assertThat(Federal.number(0, 3)).isEqualTo("000");
    }

    @Test
    void aNumberTooWideIsCutOnTheLeftSoTheRecordStaysEighty() {
        assertThat(Federal.number(1234567, 4)).isEqualTo("4567");
    }

    @Test
    void theLayoutCarriesNoSign() {
        assertThatThrownBy(() -> Federal.number(-1, 5)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void datesAreEightDigitsYearFirst() {
        assertThat(Federal.day(LocalDate.of(2025, 4, 7))).isEqualTo("20250407");
    }

    @Test
    void moneyIsWholeDollarsWithNoPoint() {
        assertThat(Federal.money(Dollars.of(46, 12))).isEqualTo("00000046");
        assertThat(Federal.money(Dollars.of(1234, 99))).isEqualTo("00001234");
    }

    @Test
    void aDetailRecordIsExactlyEightyColumns() {
        assertThat(detail().render()).hasSize(Federal.WIDTH);
    }

    @Test
    void aDetailRecordStartsWithItsKindLetter() {
        assertThat(detail().render()).startsWith("D25-P-004471");
    }

    @Test
    void aDetailRecordCarriesTheFieldsInTheirColumns() {
        String printed = detail().render();
        assertThat(printed.substring(13, 21)).isEqualTo("20250407");
        assertThat(printed.substring(21, 30)).isEqualTo("001204471");
        assertThat(printed.substring(30, 54)).isEqualTo(String.format("%-24s", "Bickford Heavy Haul"));
        assertThat(printed.charAt(54)).isEqualTo('P');
        assertThat(printed.substring(55, 62)).isEqualTo("0094000");
        assertThat(printed.substring(62, 67)).isEqualTo("00035");
        assertThat(printed.substring(67, 75)).isEqualTo("00000046");
    }

    @Test
    void aLongCarrierNameDoesNotLengthenTheRecord() {
        Federal.Detail longOne =
                detail("25-P-004471", "Aroostook County Crane and Rigging Service", Dollars.of(46, 12));
        assertThat(longOne.render()).hasSize(Federal.WIDTH);
    }

    @Test
    void aHeaderIsEightyColumnsAndNamesTheWindow() {
        String printed = new Federal.Return(APRIL, LAST, List.of()).header();
        assertThat(printed).hasSize(Federal.WIDTH);
        assertThat(printed).startsWith("H" + Federal.STATE_CODE + Federal.LAYOUT + "20250401" + "20250430");
    }

    @Test
    void aTrailerIsEightyColumnsAndCarriesTheCounts() {
        Federal.Return made = new Federal.Return(
                APRIL, LAST, List.of(detail(), detail("25-P-004472", "Bickford Heavy Haul", Dollars.of(46, 12))));
        String printed = made.trailer();
        assertThat(printed).hasSize(Federal.WIDTH);
        assertThat(printed.substring(1, 8)).isEqualTo("0000002");
        assertThat(printed.substring(8, 16)).isEqualTo("00000092");
        assertThat(printed.substring(16, 28)).isEqualTo("000000188000");
    }

    @Test
    void anEmptyReturnIsTwoRecordsAndStillValid() {
        Federal.Return made = new Federal.Return(APRIL, LAST, List.of());
        assertThat(made.lines()).hasSize(2).allSatisfy(one -> assertThat(one).hasSize(Federal.WIDTH));
        assertThat(made.count()).isZero();
        assertThat(made.fees()).isEqualTo(Dollars.of(0));
    }

    @Test
    void theTotalsAreAddedFromTheDetailsThatWereWritten() {
        Federal.Return made = new Federal.Return(
                APRIL, LAST, List.of(detail(), detail("25-P-004471", "Bickford Heavy Haul", Dollars.of(100))));
        assertThat(made.fees()).isEqualTo(Dollars.of(146, 12));
        assertThat(made.pounds()).isEqualTo(188000);
    }

    @Test
    void theFileEndsWithANewlineSoItCanBePiped() {
        assertThat(new Federal.Return(APRIL, LAST, List.of()).render()).endsWith("\n");
    }

    @Test
    void everyLineOfARenderedReturnIsEightyColumns() {
        Federal.Return made = new Federal.Return(APRIL, LAST, List.of(detail()));
        assertThat(made.render().split("\n")).allSatisfy(one -> assertThat(one).hasSize(Federal.WIDTH));
    }

    @Test
    void theReturnSaysWhatItIsInOneLine() {
        assertThat(new Federal.Return(APRIL, LAST, List.of(detail())))
                .hasToString("2025-04-01 to 2025-04-30, 1 permits");
    }

    @Test
    void aPermitRowBecomesADetailRecord() {
        Permit permit = issued();
        Federal.Detail found =
                Federal.detailOf(work.permits().row(permit.number().toString()));
        assertThat(found.number()).isEqualTo("25-P-004471");
        assertThat(found.usdot()).isEqualTo(1204471);
        assertThat(found.kind()).isEqualTo("P");
        assertThat(found.grossPounds()).isEqualTo(94000);
        assertThat(found.fee()).isEqualTo(Dollars.of(46, 12));
    }

    @Test
    void theMileageIsWholeMilesRoundedDown() {
        Permit permit = issued();
        assertThat(Federal.detailOf(work.permits().row(permit.number().toString()))
                        .miles())
                .isEqualTo(35);
    }

    @Test
    void aMonthWithNothingInItBuildsAnEmptyReturn() {
        Federal.Return made = Federal.build(db, APRIL, LAST);
        assertThat(made.count()).isZero();
        assertThat(made.lines()).hasSize(2);
    }

    @Test
    void aMonthWithOnePermitBuildsThreeRecords() {
        issued();
        Federal.Return made = Federal.build(db, APRIL, LAST);
        assertThat(made.count()).isEqualTo(1);
        assertThat(made.lines()).hasSize(3);
        assertThat(made.lines().get(1)).startsWith("D25-P-004471");
    }

    @Test
    void aPermitIssuedOutsideTheWindowIsNotInTheReturn() {
        issued();
        assertThat(Federal.build(db, LocalDate.of(2025, 5, 1), LocalDate.of(2025, 5, 31))
                        .count())
                .isZero();
    }

    @Test
    void aVoidedPermitIsStillInTheReturn() {
        Permit permit = issued();
        permit.makeVoid(LocalDate.of(2025, 4, 9), "load cancelled");
        work.permits().saveState(permit);
        db.flush();
        assertThat(Federal.build(db, APRIL, LAST).count()).isEqualTo(1);
    }

    @Test
    void thePermitsOfAWindowComeBackInNumberOrder() {
        issued("25-04472", PermitNumber.parse("25-P-004472"));
        issued();
        assertThat(Federal.issuedBetween(db, APRIL, LAST))
                .extracting(PermitRow::getNumber)
                .containsExactly("25-P-004471", "25-P-004472");
    }

    @Test
    void theCoveringLetterCountsWhatHasSinceBeenReplaced() {
        Permit permit = issued();
        assertThat(Federal.supersededCount(db, APRIL, LAST)).isZero();
        permit.supersede(LocalDate.of(2025, 4, 9), PermitNumber.parse("25-R-000001"));
        work.permits().saveState(permit);
        db.flush();
        assertThat(Federal.supersededCount(db, APRIL, LAST)).isEqualTo(1);
    }

    @Test
    void aWrittenReturnReadsBackCleanAndAnEditedOneDoesNot() {
        List<String> clean = new Federal.Return(APRIL, LAST, List.of(detail(), detail())).lines();
        assertThat(Federal.verify(clean)).isEmpty();
        List<String> edited = new ArrayList<>(clean);
        edited.set(1, edited.get(1).substring(0, 79));
        assertThat(Federal.verify(edited))
                .extracting(Federal.Problem::toString)
                .containsExactly("record 2: is 79 columns and not 80");
        List<String> missing = new ArrayList<>(clean);
        missing.remove(1);
        assertThat(Federal.verify(missing))
                .extracting(Federal.Problem::toString)
                .containsExactly(
                        "record 3: says 2 details and the file has 1",
                        "record 3: says 92 in fees and the details add to 46",
                        "record 3: says 188000 pounds and the details add to 94000");
        assertThat(Federal.verify(List.of()))
                .extracting(Federal.Problem::toString)
                .containsExactly("record 0: the file is empty");
    }

    @Test
    void theWindowOfAFileIsReadOffItsHeader() {
        List<String> lines = new Federal.Return(APRIL, LAST, List.of()).lines();
        assertThat(Federal.windowOf(lines)).isEqualTo(new Federal.Period(APRIL, LAST));
        assertThatThrownBy(() -> Federal.windowOf(lines.subList(1, 2))).isInstanceOf(Unreadable.class);
    }
}
