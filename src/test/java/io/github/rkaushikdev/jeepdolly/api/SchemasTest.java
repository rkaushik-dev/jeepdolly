package io.github.rkaushikdev.jeepdolly.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.api.Schemas.ApplicationIn;
import io.github.rkaushikdev.jeepdolly.api.Schemas.ApplicationOut;
import io.github.rkaushikdev.jeepdolly.api.Schemas.AssessmentOut;
import io.github.rkaushikdev.jeepdolly.api.Schemas.ConditionOut;
import io.github.rkaushikdev.jeepdolly.api.Schemas.PermitOut;
import io.github.rkaushikdev.jeepdolly.api.Schemas.QuoteOut;
import io.github.rkaushikdev.jeepdolly.conditions.ConditionLibrary;
import io.github.rkaushikdev.jeepdolly.domain.CommodityBook;
import io.github.rkaushikdev.jeepdolly.permits.Application;
import io.github.rkaushikdev.jeepdolly.support.Applications;
import io.github.rkaushikdev.jeepdolly.support.Permits;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The wire bodies, and the rule that every measure crosses as a string. */
class SchemasTest {

    private static final CommodityBook BOOK = CommodityBook.standard();
    private static final ConditionLibrary LIBRARY = ConditionLibrary.standard();
    private static final LocalDate FILED = LocalDate.of(2025, 4, 7);
    private static final ObjectMapper JSON = Json.mapper();

    private static ValidatorFactory validation;
    private static Validator validator;

    @BeforeAll
    static void aValidator() {
        validation = Validation.buildDefaultValidatorFactory();
        validator = validation.getValidator();
    }

    @AfterAll
    static void closeTheValidator() {
        validation.close();
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> made = new LinkedHashMap<>();
        for (int at = 0; at < pairs.length; at += 2) {
            made.put((String) pairs[at], pairs[at + 1]);
        }
        return made;
    }

    /** The excavator move as a body, every part of it editable. */
    static Map<String, Object> body() {
        List<Object> axles = new ArrayList<>(List.of(
                map("kind", "steer", "weight", "12,000 lb", "tires", 2, "tire_width", "16"),
                map("kind", "drive", "weight", "17000", "spacing_before", "200"),
                map("kind", "drive", "weight", "17000", "spacing_before", "54"),
                map("kind", "trailer", "weight", "16000", "spacing_before", "180"),
                map("kind", "trailer", "weight", "16000", "spacing_before", "52"),
                map("kind", "trailer", "weight", "16000", "spacing_before", "52")));
        return map(
                "usdot",
                1204471,
                "commodity",
                "EXCV",
                "origin",
                "waterville",
                "destination",
                "pittsfield",
                "start",
                "2025-04-08",
                "end",
                "2025-04-10",
                "applicant",
                map("name", "Dana Bickford", "phone", "207 555 0143"),
                "envelope",
                map("width", "12'", "height", "13'6\"", "length", "82 ft", "rear_overhang", "6'"),
                "axles",
                axles,
                "units",
                new ArrayList<>(List.of(
                        map("kind", "tractor", "axles", 3, "plate", "ME 4471", "tare", "19000"),
                        map("kind", "lowboy", "axles", 3, "plate", "ME 7712", "tare", "24000"))),
                "pieces",
                new ArrayList<>(List.of(map("commodity", "EXCV", "description", "Cat 336 excavator"))));
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> part(Map<String, Object> body, String key) {
        return (Map<String, Object>) body.get(key);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> item(Map<String, Object> body, String key, int index) {
        return (Map<String, Object>) ((List<Object>) body.get(key)).get(index);
    }

    private static ApplicationIn read(Map<String, Object> body) {
        return Rejected.read(JSON, validator, body, ApplicationIn.class);
    }

    @Test
    void anApplicationBodyBecomesTheDomainObject() {
        Application application = read(body()).toDomain("25-04471", FILED, BOOK);
        assertThat(application.gross()).isEqualTo(new Pounds(94000));
        assertThat(application.combination().envelope().width()).isEqualTo(Inches.of(12));
        assertThat(application.combination().stream().count()).isEqualTo(6);
        assertThat(application.pieces().get(0).commodity().code()).isEqualTo("EXCV");
    }

    @Test
    void theMeasuresAreReadInWhateverTheHaulerWrites() {
        Map<String, Object> written = body();
        part(written, "envelope").put("height", "14-2");
        item(written, "axles", 0).put("weight", "6 tons");
        Application application = read(written).toDomain("25-04471", FILED, BOOK);
        assertThat(application.combination().envelope().height()).isEqualTo(new Inches(170));
        assertThat(application.combination().stream().axles().get(0).weight()).isEqualTo(new Pounds(12000));
    }

    @Test
    void aTireWidthMayBeCopiedOffTheSidewall() {
        Map<String, Object> written = body();
        item(written, "axles", 0).put("tire_width", "425/65R22.5");
        item(written, "axles", 1).put("tire_width", "11R22.5");
        Application application = read(written).toDomain("25-04471", FILED, BOOK);
        assertThat(application.combination().stream().axles().get(0).tireWidth())
                .isEqualTo(new Inches(16));
        assertThat(application.combination().stream().axles().get(1).tireWidth())
                .isEqualTo(new Inches(11));
    }

    @Test
    void anAxleWithNoSpacingInFrontOfItIsRefused() {
        Map<String, Object> written = body();
        item(written, "axles", 2).remove("spacing_before");
        assertThatThrownBy(() -> read(written).toDomain("25-04471", FILED, BOOK))
                .isInstanceOf(Impossible.class)
                .hasMessageContaining("how far behind it stands");
    }

    @Test
    void aCommodityNobodyFiledIsRefusedWhenThePiecesAreRead() {
        Map<String, Object> written = body();
        item(written, "pieces", 0).put("commodity", "ZZZZ");
        assertThatThrownBy(() -> read(written).toDomain("25-04471", FILED, BOOK))
                .isInstanceOf(NotOnFile.class);
    }

    @Test
    void aBodyWithNoAxlesAtAllIsRefusedByTheSchema() {
        Map<String, Object> noAxles = body();
        noAxles.put("axles", List.of());
        assertThatThrownBy(() -> read(noAxles)).isInstanceOf(Rejected.class);
        Map<String, Object> noPieces = body();
        noPieces.put("pieces", List.of());
        assertThatThrownBy(() -> read(noPieces)).isInstanceOf(Rejected.class);
    }

    @Test
    void aBodyMissingSomethingItNeedsIsRefusedByTheSchema() {
        Map<String, Object> written = body();
        written.remove("usdot");
        assertThatThrownBy(() -> read(written)).isInstanceOf(Rejected.class);
    }

    @Test
    void anApplicationGoesOutWithItsMeasuresAsWords() {
        ApplicationOut out = ApplicationOut.of(Applications.anApplication());
        assertThat(out.gross()).isEqualTo("94,000 lb");
        assertThat(out.width()).isEqualTo("12 ft");
        assertThat(out.height()).isEqualTo("13 ft 6 in");
        assertThat(out.state()).isEqualTo("draft");
    }

    @Test
    void aPermitGoesOutWholeWithConditionsAndFees() {
        Application application = Applications.anApplication();
        PermitOut out = PermitOut.of(Permits.aPermit(application));
        assertThat(out.number()).isEqualTo("25-P-004471");
        assertThat(out.gross()).isEqualTo("94,000 lb");
        assertThat(out.distance()).isEqualTo("35.20 mi");
        assertThat(out.route()).containsExactly("US201-1", "US201-2", "SA148-2", "SR43-1");
        assertThat(out.conditions().get(0).code()).isEqualTo("R-01");
        assertThat(out.fees().total()).isEqualTo("$46.12");
    }

    @Test
    void theFeeLinesComeOutAsMoneyTheWayTheReceiptPrintsIt() {
        Application application = Applications.anApplication();
        QuoteOut quote = QuoteOut.of(Permits.aPermit(application).quote());
        assertThat(quote.lines().get(0).amount()).isEqualTo("$40.00");
        assertThat(quote.schedule()).isEqualTo("schedule of 2024-01-01");
    }

    @Test
    void aConditionCarriesItsCodeAndTheWordsThatWerePrinted() {
        ConditionOut out = ConditionOut.of(LIBRARY.render("G-01"));
        assertThat(out.code()).isEqualTo("G-01");
        assertThat(out.category()).isEqualTo("general");
        assertThat(out.text()).startsWith("Carry this permit");
    }

    @Test
    void anAssessmentThatWasRefusedCarriesTheReasonsAndNoPrice() {
        AssessmentOut out = new AssessmentOut(
                "25-04471", false, "none", List.of("GRVL could have been loaded to legal weight"), List.of());
        assertThat(out.fees()).isEmpty();
        assertThat(out.conditions()).isEmpty();
        assertThat(out.overweight()).isEmpty();
        assertThat(out.detour()).isEmpty();
        assertThat(out.refusals().get(0)).startsWith("GRVL");
    }
}
