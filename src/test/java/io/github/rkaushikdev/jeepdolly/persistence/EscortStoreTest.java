package io.github.rkaushikdev.jeepdolly.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Conflict;
import io.github.rkaushikdev.jeepdolly.escorts.Company;
import io.github.rkaushikdev.jeepdolly.escorts.Endorsement;
import io.github.rkaushikdev.jeepdolly.escorts.EscortRegister;
import io.github.rkaushikdev.jeepdolly.escorts.Operator;
import io.github.rkaushikdev.jeepdolly.escorts.Standing;
import io.github.rkaushikdev.jeepdolly.persistence.models.PilotCardRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.PilotCompanyRow;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Pilot car companies and cards, into the rows and back out again. */
class EscortStoreTest {

    private static final LocalDate JUNE = LocalDate.of(2025, 6, 10);

    private UnitOfWork work;
    private EntityManager db;

    @BeforeEach
    void open() {
        work = Databases.empty().unitOfWork();
        db = work.entityManager();
    }

    @AfterEach
    void close() {
        work.close();
    }

    private static Company aCompany(String ident, boolean suspended) {
        return new Company(ident, "Kennebec Pilot Car", "Waterville", "207 555 0142", suspended);
    }

    private static Company aCompany() {
        return aCompany("PC-1", false);
    }

    private static Operator anOperator(String card, String company, LocalDate expires, Set<Endorsement> endorsements) {
        return new Operator(card, "R Dubois", company, LocalDate.of(2023, 5, 1), expires, endorsements);
    }

    private static Operator anOperator() {
        return anOperator("C-1001", "PC-1", LocalDate.of(2026, 4, 30), Set.of());
    }

    private void seed(Company company, List<Operator> operators) {
        work.add(PilotCompanyRow.of(company));
        work.flush();
        for (Operator one : operators) {
            work.add(PilotCardRow.of(one));
        }
        work.flush();
    }

    @Test
    void theEndorsementsPackIntoOneSortedString() {
        assertThat(PilotCardRow.pack(Set.of())).isEqualTo("");
        assertThat(PilotCardRow.pack(Set.of(Endorsement.POLICE_LIAISON, Endorsement.HEIGHT_POLE)))
                .isEqualTo("height pole,police liaison");
    }

    @Test
    void theEndorsementsUnpackBackIntoTheSetTheyWere() {
        assertThat(PilotCardRow.unpack("height pole,police liaison"))
                .isEqualTo(Set.of(Endorsement.HEIGHT_POLE, Endorsement.POLICE_LIAISON));
        assertThat(PilotCardRow.unpack("")).isEmpty();
        assertThat(PilotCardRow.unpack("  height pole  ")).isEqualTo(Set.of(Endorsement.HEIGHT_POLE));
    }

    @Test
    void anEndorsementThisCodeDoesNotKnowIsDroppedRatherThanRaised() {
        assertThat(PilotCardRow.unpack("height pole,railroad flagging")).isEqualTo(Set.of(Endorsement.HEIGHT_POLE));
    }

    @Test
    void aCompanyGoesInAndComesBackTheSameCompany() {
        seed(aCompany(), List.of());
        assertThat(Books.escortsFrom(db).company("PC-1")).isEqualTo(aCompany());
    }

    @Test
    void aCardGoesInAndComesBackTheSameCard() {
        Operator one = anOperator("C-1001", "PC-1", LocalDate.of(2026, 4, 30), Set.of(Endorsement.HEIGHT_POLE));
        seed(aCompany(), List.of(one));
        assertThat(Books.escortsFrom(db).operator("C-1001")).isEqualTo(one);
    }

    @Test
    void theRegisterReadsTheStandingOffTheRows() {
        seed(aCompany(), List.of(anOperator()));
        assertThat(Books.escortsFrom(db).standing("C-1001", JUNE)).isSameAs(Standing.GOOD);
    }

    @Test
    void aSuspendedCompanyRowSuspendsItsCardsOnTheWayBack() {
        seed(aCompany("PC-1", true), List.of(anOperator()));
        assertThat(Books.escortsFrom(db).standing("C-1001", JUNE)).isSameAs(Standing.COMPANY_SUSPENDED);
    }

    @Test
    void aCardThatRanOutIsStillOnTheRegister() {
        seed(aCompany(), List.of(anOperator("C-1001", "PC-1", LocalDate.of(2024, 12, 31), Set.of())));
        EscortRegister register = Books.escortsFrom(db);
        assertThat(register.contains("C-1001")).isTrue();
        assertThat(register.standing("C-1001", JUNE)).isSameAs(Standing.EXPIRED);
    }

    @Test
    void everyCardOfACompanyComesBackUnderIt() {
        seed(aCompany(), List.of(anOperator(), anOperator("C-1002", "PC-1", LocalDate.of(2026, 4, 30), Set.of())));
        assertThat(Books.escortsFrom(db).operators("PC-1")).hasSize(2);
    }

    @Test
    void twoCompaniesKeepTheirOwnCards() {
        seed(aCompany(), List.of(anOperator()));
        work.add(PilotCompanyRow.of(aCompany("PC-2", false)));
        work.flush();
        work.add(PilotCardRow.of(anOperator("C-2001", "PC-2", LocalDate.of(2026, 4, 30), Set.of())));
        work.flush();
        EscortRegister register = Books.escortsFrom(db);
        assertThat(register.operators("PC-2")).extracting(Operator::card).containsExactly("C-2001");
        assertThat(register.size()).isEqualTo(2);
    }

    @Test
    void aRegisterBuiltFromAnEmptyDatabaseHoldsNothing() {
        EscortRegister register = Books.escortsFrom(db);
        assertThat(register.size()).isZero();
        assertThat(register.companies()).isEmpty();
    }

    @Test
    void aCardWithABadRunOfDatesIsRefusedBeforeItReachesARow() {
        assertThatThrownBy(() -> anOperator("C-1001", "PC-1", LocalDate.of(2022, 1, 1), Set.of()))
                .isInstanceOf(Conflict.class);
    }

    @Test
    void theCompanyRowCarriesItsCardsForACascade() {
        seed(aCompany(), List.of(anOperator()));
        // Hibernate keeps the company row it was handed, which never listed the card,
        // so the row is read back fresh, which loads the collection from the table.
        db.clear();
        PilotCompanyRow row = db.find(PilotCompanyRow.class, "PC-1");
        assertThat(row.getCards()).extracting(PilotCardRow::getCard).containsExactly("C-1001");
        work.delete(row);
        work.flush();
        assertThat(db.find(PilotCardRow.class, "C-1001")).isNull();
    }

    @Test
    void theRowsSayWhatTheyAreWhenSomethingPrintsThem() {
        seed(aCompany(), List.of(anOperator()));
        assertThat(db.find(PilotCompanyRow.class, "PC-1")).hasToString("<PilotCompanyRow PC-1>");
        assertThat(db.find(PilotCardRow.class, "C-1001")).hasToString("<PilotCardRow C-1001>");
    }
}
