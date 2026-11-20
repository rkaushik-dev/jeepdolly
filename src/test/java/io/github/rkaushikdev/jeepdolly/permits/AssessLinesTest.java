package io.github.rkaushikdev.jeepdolly.permits;

import static io.github.rkaushikdev.jeepdolly.support.Applications.anApplication;
import static io.github.rkaushikdev.jeepdolly.support.Applications.application;
import static io.github.rkaushikdev.jeepdolly.support.Offices.anOffice;
import static io.github.rkaushikdev.jeepdolly.support.Offices.office;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.conditions.Condition;
import io.github.rkaushikdev.jeepdolly.domain.Combination;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import io.github.rkaushikdev.jeepdolly.support.Combinations;
import io.github.rkaushikdev.jeepdolly.support.Streams;
import io.github.rkaushikdev.jeepdolly.utilities.Crossing;
import io.github.rkaushikdev.jeepdolly.utilities.LineBook;
import io.github.rkaushikdev.jeepdolly.utilities.LineOwner;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** What the assessment makes of the wires over the route. */
class AssessLinesTest {

    private static final LocalDate APRIL = LocalDate.of(2025, 4, 8);
    // The load runs waterville to pittsfield, which the router sends up the interstate, so the
    // wire that matters is the one on I95-2.
    private static final LocalDate LATE_FILED = LocalDate.of(2025, 4, 7);
    private static final LocalDate EARLY_FILED = LocalDate.of(2025, 3, 1);

    private static Combination load(String height) {
        return Combinations.aCombination(Streams.tractorLowboy(), Envelope.read("12'", height, "82'", "0", "6'"));
    }

    private static List<String> codes(Assessment found) {
        return found.conditions().stream().map(Condition::code).toList();
    }

    private static LineBook bookWith() {
        return bookWith("19'", Optional.of("26'"), "I95-2", 5);
    }

    private static LineBook bookWith(String height, Optional<String> liftTo, String segment, int ownerNotice) {
        LineBook made = new LineBook();
        made.addOwner(new LineOwner("CMP", "Central Maine Power", "207 555 0100", ownerNotice));
        Inches hangs = Inches.parse(height);
        made.add(liftTo.map(lift -> new Crossing("L-9", segment, "Burnham line", hangs, "CMP", Inches.parse(lift)))
                .orElseGet(() -> new Crossing("L-9", segment, "Burnham line", hangs, "CMP")));
        return made;
    }

    private static LineBook twoLifts() {
        LineBook made = bookWith();
        made.add(new Crossing("L-8", "I95-1", "Waterville flats", Inches.of(20), "CMP", Inches.of(26)));
        return made;
    }

    private static Application tall(String height, LocalDate filed) {
        return application().combination(load(height)).filed(filed).build();
    }

    @Test
    void anOrdinaryLoadGoesUnderEverythingAndSaysNothing() {
        Assessment found = Assessor.assess(anOffice(), anApplication(), APRIL);
        assertThat(found.permittable()).isTrue();
        assertThat(found.lines()).isPresent();
        assertThat(found.lines().orElseThrow().lifts()).isEmpty();
        assertThat(codes(found)).doesNotContain("U-01");
    }

    @Test
    void aLoadThatNeedsALiftIsPermittedWithATelephoneNumber() {
        Assessment found = Assessor.assess(office().lines(bookWith()).build(), tall("20'", EARLY_FILED), APRIL);
        assertThat(found.permittable()).isTrue();
        assertThat(found.lines()).isPresent();
        assertThat(found.lines().orElseThrow().lifts()).hasSize(1);
        Condition printed = found.conditions().stream()
                .filter(one -> one.code().equals("U-01"))
                .findFirst()
                .orElseThrow();
        assertThat(printed.text())
                .isEqualTo("Line lift at I95-2 at Burnham line. Ring Central Maine Power on 207 555 0100.");
        assertThat(codes(found)).contains("U-02");
    }

    @Test
    void aLoadThatNeedsNoLiftPrintsNoReminder() {
        Assessment found = Assessor.assess(office().lines(bookWith()).build(), anApplication(), APRIL);
        assertThat(codes(found)).doesNotContain("U-02");
    }

    @Test
    void aWireNothingCanBeDoneAboutRefusesTheApplication() {
        Assessment found = Assessor.assess(
                office().lines(bookWith("19'", Optional.empty(), "I95-2", 5)).build(), tall("26'", EARLY_FILED), APRIL);
        assertThat(found.permittable()).isFalse();
        assertThat(found.refusals()).anyMatch(one -> one.contains("hangs at"));
    }

    @Test
    void aLiftAskedForTooLateRefusesTheApplication() {
        Assessment found = Assessor.assess(office().lines(bookWith()).build(), tall("20'", LATE_FILED), APRIL);
        assertThat(found.permittable()).isFalse();
        assertThat(found.refusals()).anyMatch(one -> one.contains("working days notice"));
    }

    @Test
    void theSameLoadFiledInTimeIsPermitted() {
        assertThat(Assessor.assess(office().lines(bookWith()).build(), tall("20'", EARLY_FILED), APRIL)
                        .permittable())
                .isTrue();
    }

    @Test
    void anOwnerWantingNoNoticeCanBeRungTheDayBefore() {
        Office desk =
                office().lines(bookWith("19'", Optional.of("26'"), "I95-2", 0)).build();
        assertThat(Assessor.assess(desk, tall("20'", LATE_FILED), APRIL).permittable())
                .isTrue();
    }

    @Test
    void aWireOnARoadTheRouteMissesIsNobodysBusiness() {
        Office desk =
                office().lines(bookWith("19'", Optional.empty(), "SR8-1", 5)).build();
        Assessment found = Assessor.assess(desk, tall("26'", EARLY_FILED), APRIL);
        assertThat(found.refusals()).noneMatch(one -> one.contains("hangs at"));
    }

    @Test
    void twoLiftsOnOneRouteBothPrintWithTheirOwnPlace() {
        Assessment found = Assessor.assess(office().lines(twoLifts()).build(), tall("21'", EARLY_FILED), APRIL);
        List<Condition> printed = found.conditions().stream()
                .filter(one -> one.code().equals("U-01"))
                .toList();
        assertThat(printed).hasSize(2);
        Set<String> texts = printed.stream().map(Condition::text).collect(Collectors.toSet());
        assertThat(texts).isNotEqualTo(Set.of(printed.get(0).text()));
    }

    @Test
    void theReminderIsPrintedOnceHoweverManyLiftsThereAre() {
        Assessment found = Assessor.assess(office().lines(twoLifts()).build(), tall("21'", EARLY_FILED), APRIL);
        assertThat(Collections.frequency(codes(found), "U-02")).isEqualTo(1);
    }
}
