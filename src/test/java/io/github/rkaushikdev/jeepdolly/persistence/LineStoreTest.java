package io.github.rkaushikdev.jeepdolly.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Conflict;
import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.persistence.models.LineCrossingRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.LineOwnerRow;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import io.github.rkaushikdev.jeepdolly.utilities.Crossing;
import io.github.rkaushikdev.jeepdolly.utilities.LineBook;
import io.github.rkaushikdev.jeepdolly.utilities.LineOwner;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Line owners and wires, into the rows and back out again. */
class LineStoreTest {

    private static final LocalDate SURVEYED = LocalDate.of(2024, 9, 12);

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

    private static LineOwner anOwner() {
        return anOwner("CMP", 5);
    }

    private static LineOwner anOwner(String ident, int notice) {
        return new LineOwner(ident, "Central Maine Power", "207 555 0100", notice);
    }

    private static Crossing aCrossing() {
        return aCrossing("L-1", "US201-2", "18'", "24'", "CMP");
    }

    private static Crossing aCrossing(String ident, String segment, String height, String liftTo, String owner) {
        return new Crossing(
                ident,
                segment,
                "Fairfield village",
                Inches.parse(height),
                owner,
                Optional.ofNullable(liftTo).map(Inches::parse),
                "");
    }

    private void seed(List<LineOwner> owners, List<Crossing> crossings) {
        for (LineOwner one : owners) {
            work.add(LineOwnerRow.of(one));
        }
        work.flush();
        for (Crossing one : crossings) {
            work.add(LineCrossingRow.of(one, SURVEYED));
        }
        work.flush();
    }

    private void seed(List<Crossing> crossings) {
        seed(List.of(anOwner()), crossings);
    }

    @Test
    void anOwnerGoesInAndComesBackTheSameOwner() {
        seed(List.of());
        assertThat(Books.linesFrom(db).owner("CMP")).isEqualTo(anOwner());
    }

    @Test
    void aCrossingGoesInAndComesBackTheSameCrossing() {
        Crossing one = aCrossing();
        seed(List.of(one));
        assertThat(Books.linesFrom(db).get("L-1")).isEqualTo(one);
    }

    @Test
    void aWireThatCannotBeLiftedKeepsItsNull() {
        seed(List.of(aCrossing("L-1", "US201-2", "18'", null, "CMP")));
        Crossing found = Books.linesFrom(db).get("L-1");
        assertThat(found.liftTo()).isEmpty();
        assertThat(found.liftable()).isFalse();
    }

    @Test
    void theMeasuredDayIsKeptEvenThoughNothingReadsItYet() {
        seed(List.of(aCrossing()));
        assertThat(db.find(LineCrossingRow.class, "L-1").getMeasured()).contains(SURVEYED);
    }

    @Test
    void aWireOverASegmentNobodyHasIsRefusedByTheDatabase() {
        seed(List.of());
        work.add(LineCrossingRow.of(aCrossing("L-1", "XX-9", "18'", "24'", "CMP"), SURVEYED));
        assertThatThrownBy(work::flush).isInstanceOf(IntegrityViolation.class);
    }

    @Test
    void aWireHangingAtNothingIsRefusedBeforeItReachesARow() {
        assertThatThrownBy(() -> aCrossing("L-1", "US201-2", "0", "24'", "CMP")).isInstanceOf(Conflict.class);
    }

    @Test
    void theCrossingsOfASegmentComeBackTogether() {
        seed(List.of(aCrossing(), aCrossing("L-2", "US2-1", "17'", "24'", "CMP")));
        LineBook book = Books.linesFrom(db);
        assertThat(book.onSegment("US201-2")).extracting(Crossing::ident).containsExactly("L-1");
        assertThat(book.size()).isEqualTo(2);
    }

    @Test
    void twoOwnersKeepTheirOwnWires() {
        seed(
                List.of(anOwner(), anOwner("TEL", 3)),
                List.of(aCrossing(), aCrossing("L-2", "US201-2", "18'", "24'", "TEL")));
        LineBook book = Books.linesFrom(db);
        assertThat(book.get("L-2").owner()).isEqualTo("TEL");
        assertThat(book.owner("TEL").noticeDays()).isEqualTo(3);
    }

    @Test
    void theOwnersComeBackInIdentOrder() {
        seed(List.of(anOwner("TEL", 5), anOwner()), List.of());
        assertThat(Books.linesFrom(db).owners()).extracting(LineOwner::ident).containsExactly("CMP", "TEL");
    }

    @Test
    void anEmptyDatabaseHasNoWiresAndNoLowestOne() {
        LineBook book = Books.linesFrom(db);
        assertThat(book.size()).isZero();
        assertThat(book.lowest()).isEmpty();
    }

    @Test
    void theOwnerRowCarriesItsWiresForACascade() {
        seed(List.of(aCrossing()));
        // Hibernate keeps the owner row it was handed, which never listed the wire; the original
        // loaded the collection on first touch. Reading the row back fresh is the same question.
        db.clear();
        LineOwnerRow row = db.find(LineOwnerRow.class, "CMP");
        assertThat(row.getWires()).extracting(LineCrossingRow::getIdent).containsExactly("L-1");
        work.delete(row);
        work.flush();
        assertThat(db.find(LineCrossingRow.class, "L-1")).isNull();
    }

    @Test
    void theRowAnswersWhatItHangsAt() {
        seed(List.of(aCrossing()));
        assertThat(db.find(LineCrossingRow.class, "L-1").getClearance()).isEqualTo(Inches.of(18));
    }

    @Test
    void theRowsSayWhatTheyAreWhenSomethingPrintsThem() {
        seed(List.of(aCrossing()));
        assertThat(db.find(LineOwnerRow.class, "CMP")).hasToString("<LineOwnerRow CMP>");
        assertThat(db.find(LineCrossingRow.class, "L-1")).hasToString("<LineCrossingRow L-1>");
    }
}
