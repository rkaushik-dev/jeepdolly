package io.github.rkaushikdev.jeepdolly.permits;

import static io.github.rkaushikdev.jeepdolly.permits.Printing.WIDTH;
import static io.github.rkaushikdev.jeepdolly.permits.Reprint.FREELY;
import static io.github.rkaushikdev.jeepdolly.support.Applications.anApplication;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.NotAllowed;
import io.github.rkaushikdev.jeepdolly.conditions.Condition;
import io.github.rkaushikdev.jeepdolly.support.Permits;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Duplicate copies, which the office would rather not issue and has to. */
class ReprintTest {

    private static final LocalDate INSIDE = LocalDate.of(2025, 4, 9);
    private static final LocalDate AFTER = LocalDate.of(2025, 5, 1);

    private static Permit permit() {
        return Permits.aPermit(anApplication());
    }

    private static Reprint aReprint(int copy) {
        return aReprint(copy, Reason.LOST, "", "D Bickford");
    }

    private static Reprint aReprint(int copy, Reason reason, String note, String askedBy) {
        return new Reprint("25-P-004471", copy, INSIDE, reason, askedBy, "M Cyr", note);
    }

    private static Reprint aReprint(Reason reason, String note) {
        return aReprint(2, reason, note, "D Bickford");
    }

    @Test
    void theReasonsAreTheWordsTheCounterUses() {
        assertThat(Reason.LEFT_BEHIND).hasToString("left in another unit");
        assertThat(Reason.OTHER.wantsWords()).isTrue();
        assertThat(Reason.LOST.wantsWords()).isFalse();
    }

    @Test
    void theOriginalIsCopyOneAndIsNotAReprint() {
        assertThatThrownBy(() -> aReprint(1)).isInstanceOf(Impossible.class);
    }

    @Test
    void aReprintNamesWhoAskedForIt() {
        assertThatThrownBy(() -> aReprint(2, Reason.LOST, "", "  ")).isInstanceOf(Impossible.class);
    }

    @Test
    void aReprintForAnotherReasonSaysWhatTheReasonWas() {
        assertThatThrownBy(() -> aReprint(Reason.OTHER, "")).isInstanceOf(Impossible.class);
        assertThat(aReprint(Reason.OTHER, "wanted one for the file").copy()).isEqualTo(2);
    }

    @Test
    void aReprintPrintsTheWayTheRegisterReads() {
        assertThat(aReprint(2)).hasToString("25-P-004471 copy 2  2025-04-09  lost");
        assertThat(aReprint(Reason.OTHER, "for the file").toString()).endsWith("other, for the file");
    }

    @Test
    void theBannerSaysWhichCopyItIs() {
        assertThat(aReprint(3).banner()).isEqualTo("DUPLICATE - COPY 3");
    }

    @Test
    void aCopyInsideTheThresholdWantsNoSupervisor() {
        assertThat(aReprint(FREELY).neededASupervisor()).isFalse();
    }

    @Test
    void aCopyPastItDoes() {
        assertThat(aReprint(FREELY + 1).neededASupervisor()).isTrue();
    }

    @Test
    void theFirstReprintIsCopyTwo() {
        assertThat(Reprint.nextCopy(List.of())).isEqualTo(2);
    }

    @Test
    void theNextCopyComesOffTheHighestAndNotOffTheCount() {
        assertThat(Reprint.nextCopy(List.of(2, 3))).isEqualTo(4);
        assertThat(Reprint.nextCopy(List.of(3))).isEqualTo(4);
    }

    @Test
    void aLivePermitMayBeCopied() {
        assertThatCode(() -> Reprint.allowed(permit(), INSIDE, 2, false)).doesNotThrowAnyException();
    }

    @Test
    void aVoidPermitMayNot() {
        Permit one = permit();
        one.makeVoid(INSIDE, "load cancelled");
        assertThatThrownBy(() -> Reprint.allowed(one, INSIDE, 2, false)).isInstanceOf(NotAllowed.class);
    }

    @Test
    void anExpiredPermitMayNotEvenForTheHaulersOwnFile() {
        assertThatThrownBy(() -> Reprint.allowed(permit(), AFTER, 2, false))
                .isInstanceOf(NotAllowed.class)
                .hasMessageContaining("expired on");
    }

    @Test
    void aFourthCopyWantsASupervisor() {
        assertThatThrownBy(() -> Reprint.allowed(permit(), INSIDE, FREELY + 1, false))
                .isInstanceOf(NotAllowed.class)
                .hasMessageContaining("wants a supervisor");
    }

    @Test
    void aSupervisorMayPrintTheFourth() {
        assertThatCode(() -> Reprint.allowed(permit(), INSIDE, FREELY + 1, true))
                .doesNotThrowAnyException();
    }

    @Test
    void reprintingWorksOutTheCopyNumberAndRecordsIt() {
        Reprint found = Reprint.issue(permit(), INSIDE, Reason.DAMAGED, "D Bickford", "M Cyr");
        assertThat(found.copy()).isEqualTo(2);
        assertThat(found.permit()).isEqualTo("25-P-004471");
        assertThat(found.reason()).isSameAs(Reason.DAMAGED);
    }

    @Test
    void reprintingAPermitThatAlreadyHasCopiesCarriesOnTheSequence() {
        Reprint found = Reprint.issue(permit(), INSIDE, Reason.LOST, "D Bickford", "M Cyr", List.of(2));
        assertThat(found.copy()).isEqualTo(3);
        assertThat(found.neededASupervisor()).isFalse();
    }

    @Test
    void reprintingPastTheThresholdWithoutASupervisorIsRefused() {
        assertThatThrownBy(() -> Reprint.issue(permit(), INSIDE, Reason.LOST, "D Bickford", "M Cyr", List.of(2, 3, 4)))
                .isInstanceOf(NotAllowed.class);
    }

    @Test
    void reprintingPastItWithOneIsAllowed() {
        Reprint found = Reprint.issue(permit(), INSIDE, Reason.LOST, "D Bickford", "M Cyr", List.of(2, 3, 4), true);
        assertThat(found.copy()).isEqualTo(5);
        assertThat(found.neededASupervisor()).isTrue();
    }

    @Test
    void aReprintNeverTouchesThePermit() {
        Permit one = permit();
        PermitState state = one.state();
        PermitNumber number = one.number();
        List<Condition> conditions = one.conditions();
        Reprint.issue(one, INSIDE, Reason.LOST, "D Bickford", "M Cyr");
        assertThat(List.of(one.state(), one.number(), one.conditions())).isEqualTo(List.of(state, number, conditions));
    }

    /** What the duplicate sheet says that the original does not. */
    @Nested
    class ThePrintedCopy {

        @Test
        void theOriginalCarriesNoBanner() {
            assertThat(Printing.sheet(permit())).doesNotContain("DUPLICATE");
        }

        @Test
        void aCopyCarriesOneAtTheTopAndOneAtTheFoot() {
            List<String> lines = Printing.printed(permit(), 2);
            assertThat(lines.get(3).strip()).isEqualTo("*** DUPLICATE - COPY 2 ***");
            assertThat(lines.get(lines.size() - 2).strip()).isEqualTo("*** DUPLICATE - COPY 2 ***");
        }

        @Test
        void theCopyNumberIsOnBothBanners() {
            String written = Printing.sheet(permit(), 4);
            assertThat(written.split("\\*\\*\\* DUPLICATE - COPY 4 \\*\\*\\*", -1))
                    .hasSize(3);
        }

        @Test
        void aCopyIsStillEightyColumns() {
            assertThat(Printing.printed(permit(), 2))
                    .allSatisfy(one -> assertThat(one.length()).isLessThanOrEqualTo(WIDTH));
        }

        @Test
        void aVoidCopySaysBothThings() {
            Permit one = permit();
            one.makeVoid(INSIDE, "load cancelled");
            String written = Printing.sheet(one, 2);
            assertThat(written).contains("*** DUPLICATE - COPY 2 ***");
            assertThat(written).contains("*** VOID ***");
        }

        @Test
        void theConditionsAreTheSameOnACopy() {
            assertThat(Collections.frequency(Printing.printed(permit()), "CONDITIONS"))
                    .isEqualTo(Collections.frequency(Printing.printed(permit(), 2), "CONDITIONS"));
        }
    }
}
