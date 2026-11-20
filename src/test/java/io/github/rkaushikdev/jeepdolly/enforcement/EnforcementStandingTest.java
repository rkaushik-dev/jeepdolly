package io.github.rkaushikdev.jeepdolly.enforcement;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.permits.Assessment;
import io.github.rkaushikdev.jeepdolly.permits.Assessor;
import io.github.rkaushikdev.jeepdolly.permits.Office;
import io.github.rkaushikdev.jeepdolly.persistence.Books;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.services.Enforcement;
import io.github.rkaushikdev.jeepdolly.support.Applications;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import io.github.rkaushikdev.jeepdolly.support.Offices;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Where a carrier stands on what it owes, and when that holds a permit. */
class EnforcementStandingTest {

    private static final LocalDate WRITTEN = LocalDate.of(2025, 4, 8);
    private static final LocalDate DUE = LocalDate.of(2025, 5, 29);
    private static final LocalDate APRIL = LocalDate.of(2025, 4, 8);
    private static final String CLERK = "M Cyr";

    private static Owed owed() {
        return owed("E-25-0417", WRITTEN, 580, false);
    }

    private static Owed owed(String number, LocalDate written, int amount, boolean contested) {
        return new Owed(
                number, written, Dollars.of(amount), Standing.dueDay(written, Citation.CONTEST_DAYS), contested);
    }

    private static Account account(List<Owed> unpaid) {
        return new Account(1204471, unpaid);
    }

    @Test
    void theDueDayIsTheContestWindowAndTheGraceAfterIt() {
        assertThat(Standing.dueDay(WRITTEN, Citation.CONTEST_DAYS)).isEqualTo(DUE);
        assertThat(ChronoUnit.DAYS.between(WRITTEN, DUE)).isEqualTo(Citation.CONTEST_DAYS + Standing.GRACE_DAYS);
    }

    @Test
    void aCitationIsNotPastDueOnTheDayItFallsDue() {
        Owed one = owed();
        assertThat(one.pastDueOn(DUE)).isFalse();
        assertThat(one.pastDueOn(LocalDate.of(2025, 5, 30))).isTrue();
    }

    @Test
    void aContestedCitationIsNeverPastDueHoweverOld() {
        assertThat(owed("E-25-0417", WRITTEN, 580, true).pastDueOn(LocalDate.of(2026, 1, 1)))
                .isFalse();
    }

    @Test
    void anUnpaidCitationPrintsItsDueDay() {
        assertThat(owed()).hasToString("E-25-0417  $580.00  due 2025-05-29");
        assertThat(owed("E-25-0417", WRITTEN, 580, true).toString()).endsWith("(contested)");
    }

    @Test
    void aCarrierWithNothingOwesNothingAndIsNotHeld() {
        Account one = account(List.of());
        assertThat(one.owing()).isEqualTo(Dollars.of(0));
        assertThat(one.hold(LocalDate.of(2026, 1, 1))).isEmpty();
        assertThat(one).hasToString("1204471 owes nothing");
    }

    @Test
    void whatACarrierOwesAddsUp() {
        Account one = account(List.of(owed(), owed("E-25-0500", WRITTEN, 250, false)));
        assertThat(one.owing()).isEqualTo(Dollars.of(830));
        assertThat(one).hasToString("1204471 owes $830.00 on 2 citations");
    }

    @Test
    void aLateCitationDoesNotHoldUntilTheGraceHasRun() {
        Account one = account(List.of(owed()));
        assertThat(one.hold(LocalDate.of(2025, 5, 1))).isEmpty();
        assertThat(one.hold(LocalDate.of(2025, 6, 1))).isPresent();
    }

    @Test
    void theHoldNamesTheOldestOneAndCountsThemAll() {
        Account one = account(List.of(owed(), owed("E-25-0100", LocalDate.of(2025, 1, 20), 580, false)));
        String line = one.hold(LocalDate.of(2025, 6, 1)).orElseThrow();
        assertThat(line).contains("E-25-0100");
        assertThat(line).contains("2 unpaid citations");
    }

    @Test
    void aContestedCitationDoesNotHoldACarrierAtAll() {
        Account one = account(List.of(owed("E-25-0417", WRITTEN, 580, true)));
        assertThat(one.hold(LocalDate.of(2026, 1, 1))).isEmpty();
        assertThat(one.owing()).isEqualTo(Dollars.of(580));
    }

    @Test
    void theBookAnswersForACarrierItHasNeverHeardOf() {
        EnforcementBook book = new EnforcementBook();
        assertThat(book.account(9999999).owing()).isEqualTo(Dollars.of(0));
        assertThat(book.hold(9999999, LocalDate.of(2026, 1, 1))).isEmpty();
        assertThat(book.contains(9999999)).isFalse();
    }

    @Test
    void theBookHoldsWhatItWasGiven() {
        EnforcementBook book = new EnforcementBook();
        book.add(account(List.of(owed())));
        assertThat(book.size()).isEqualTo(1);
        assertThat(book.contains(1204471)).isTrue();
        assertThat(book.hold(1204471, LocalDate.of(2025, 6, 1))).isPresent();
    }

    @Test
    void everybodyHeldComesBackWorstFirst() {
        EnforcementBook book = new EnforcementBook();
        book.add(account(List.of(owed())));
        book.add(new Account(
                2299001, List.of(owed("E-25-0600", WRITTEN, 580, false), owed("E-25-0601", WRITTEN, 580, false))));
        assertThat(book.held(LocalDate.of(2025, 6, 1)))
                .extracting(Account::usdot)
                .containsExactly(2299001, 1204471);
    }

    @Test
    void nobodyIsHeldBeforeAnythingFallsDue() {
        EnforcementBook book = new EnforcementBook();
        book.add(account(List.of(owed())));
        assertThat(book.held(LocalDate.of(2025, 5, 1))).isEmpty();
    }

    /** The book built off the rows, and what the assessment makes of it. */
    @Nested
    class AgainstTheDatabase {

        private UnitOfWork work;

        @BeforeEach
        void open() {
            work = Databases.seeded().unitOfWork();
        }

        @AfterEach
        void close() {
            work.close();
        }

        private void fileOne() {
            fileOne("E-25-0417", WRITTEN);
        }

        private void fileOne(String number, LocalDate on) {
            Enforcement.fileCitation(
                    work,
                    number,
                    1204471,
                    "Sgt A Thibodeau",
                    Offence.OVERWEIGHT,
                    "US201-2 at Fairfield",
                    on,
                    on,
                    CLERK,
                    Optional.of(new Pounds(12000)),
                    Optional.empty(),
                    "");
            work.entityManager().flush();
        }

        private EnforcementBook book() {
            return Books.enforcementFrom(work.entityManager());
        }

        private Assessment assessed() {
            Office office = Offices.office().enforcement(book()).build();
            return Assessor.assess(office, Applications.anApplication(), APRIL);
        }

        @Test
        void anEmptyDatabaseHoldsNobody() {
            assertThat(book().size()).isZero();
        }

        @Test
        void anUnpaidCitationShowsUpInTheAccount() {
            fileOne();
            assertThat(book().account(1204471).owing()).isEqualTo(Dollars.of(580));
        }

        @Test
        void aPaidCitationIsNotInTheAccountAtAll() {
            fileOne();
            Enforcement.pay(work, "E-25-0417", LocalDate.of(2025, 4, 22), CLERK);
            work.entityManager().flush();
            assertThat(book().size()).isZero();
        }

        @Test
        void aDismissedCitationIsNotInTheAccount() {
            fileOne();
            Enforcement.contest(work, "E-25-0417", LocalDate.of(2025, 4, 20), "grounds", CLERK);
            Enforcement.dismiss(work, "E-25-0417", LocalDate.of(2025, 5, 2), "scale", CLERK);
            work.entityManager().flush();
            assertThat(book().size()).isZero();
        }

        @Test
        void aContestedCitationIsInTheAccountAndHoldsNobody() {
            fileOne();
            Enforcement.contest(work, "E-25-0417", LocalDate.of(2025, 4, 20), "grounds", CLERK);
            work.entityManager().flush();
            EnforcementBook found = book();
            assertThat(found.account(1204471).owing()).isEqualTo(Dollars.of(580));
            assertThat(found.hold(1204471, LocalDate.of(2026, 1, 1))).isEmpty();
        }

        @Test
        void aPermitIsWrittenWhileACitationIsMerelyLate() {
            fileOne();
            assertThat(assessed().permittable()).isTrue();
        }

        @Test
        void aPermitIsHeldOnceTheCitationIsPastDue() {
            fileOne("E-25-0417", LocalDate.of(2025, 1, 20));
            Assessment found = assessed();
            assertThat(found.permittable()).isFalse();
            assertThat(found.refusals()).anySatisfy(one -> assertThat(one).contains("E-25-0417"));
        }

        @Test
        void payingItLiftsTheHoldTheSameDay() {
            fileOne("E-25-0417", LocalDate.of(2025, 1, 20));
            Enforcement.pay(work, "E-25-0417", APRIL, CLERK);
            work.entityManager().flush();
            assertThat(assessed().permittable()).isTrue();
        }

        @Test
        void anotherCarriersDebtDoesNotHoldThisOne() {
            Enforcement.fileCitation(
                    work,
                    "E-25-0600",
                    2299001,
                    "Sgt A Thibodeau",
                    Offence.NO_PERMIT,
                    "US2-1",
                    LocalDate.of(2025, 1, 20),
                    LocalDate.of(2025, 1, 20),
                    CLERK);
            work.entityManager().flush();
            assertThat(assessed().permittable()).isTrue();
        }
    }
}
