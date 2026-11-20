package io.github.rkaushikdev.jeepdolly.persistence.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.domain.Commodity;
import io.github.rkaushikdev.jeepdolly.domain.CommodityBook;
import io.github.rkaushikdev.jeepdolly.domain.Family;
import io.github.rkaushikdev.jeepdolly.persistence.IntegrityViolation;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The reference tables, and the rows that go on and off them. */
class ReferenceModelsTest {

    private static final LocalDate MARCH = LocalDate.of(2025, 3, 3);

    private UnitOfWork session;

    @BeforeEach
    void open() {
        session = Databases.empty().unitOfWork();
    }

    @AfterEach
    void close() {
        session.close();
    }

    private CommodityRow commodity(String code) {
        return session.entityManager().find(CommodityRow.class, code);
    }

    @Test
    void aCommodityGoesToARowAndComesBackTheSame() {
        Commodity excavator = CommodityBook.standard().get("EXCV");
        session.add(CommodityRow.of(excavator, "clerk", MARCH));
        session.commit();
        session.entityManager().clear();
        CommodityRow row = commodity("EXCV");
        assertThat(row.toDomain()).isEqualTo(excavator);
        assertThat(row.getChangedBy()).isEqualTo("clerk");
        assertThat(row.getChangedOn()).contains(MARCH);
    }

    @Test
    void theFlagsThatLaterRulesReadSurviveTheRoundTrip() {
        for (String code : List.of("XFMR", "WOOD", "GRVL", "BOAT")) {
            session.add(CommodityRow.of(CommodityBook.standard().get(code)));
        }
        session.commit();
        session.entityManager().clear();
        Commodity transformer = commodity("XFMR").toDomain();
        Commodity wood = commodity("WOOD").toDomain();
        assertThat(transformer.tipLimitDegrees()).contains(3);
        assertThat(wood.divisible()).isTrue();
        assertThat(wood.allowance()).contains("forest products");
        assertThat(commodity("BOAT").toDomain().highCentre()).isTrue();
    }

    @Test
    void aWholeTableCanBeWrittenAndReadBack() {
        CommodityBook book = CommodityBook.standard();
        for (String code : book.codes()) {
            session.add(CommodityRow.of(book.get(code)));
        }
        session.commit();
        List<CommodityRow> rows = session.entityManager()
                .createQuery("select c from CommodityRow c order by c.code", CommodityRow.class)
                .getResultList();
        assertThat(rows).hasSize(book.size());
        assertThat(rows).extracting(CommodityRow::getCode).isEqualTo(book.codes());
    }

    @Test
    void aRetiredCommodityIsKeptAndMarkedRatherThanDeleted() {
        session.add(CommodityRow.of(CommodityBook.standard().get("CHIP")));
        session.commit();
        CommodityRow row = commodity("CHIP");
        assertThat(row.isLive()).isTrue();
        row.setRetired(MARCH);
        session.commit();
        assertThat(commodity("CHIP").isLive()).isFalse();
        assertThat(session.entityManager()
                        .createQuery("select c from CommodityRow c", CommodityRow.class)
                        .getResultList())
                .isNotEmpty();
    }

    @Test
    void theFamilyIsStoredAsItsOwnShortWord() {
        session.add(CommodityRow.of(CommodityBook.standard().get("EXCV")));
        session.commit();
        Object row = session.entityManager()
                .createNativeQuery("select family from commodities")
                .getSingleResult();
        assertThat(row).isEqualTo("machinery");
        assertThat(commodity("EXCV").getFamily()).isSameAs(Family.MACHINERY);
    }

    @Test
    void anImpossibleTipLimitIsRefusedByTheDatabase() {
        CommodityRow bad = new CommodityRow("BAD", "nonsense", Family.MACHINERY);
        bad.setTipLimitDegrees(90);
        session.add(bad);
        assertThatThrownBy(session::commit).isInstanceOf(IntegrityViolation.class);
    }

    @Test
    void aCommodityWithNoCodeIsRefusedByTheDatabase() {
        session.add(new CommodityRow("", "nonsense", Family.MACHINERY));
        assertThatThrownBy(session::commit).isInstanceOf(IntegrityViolation.class);
    }

    @Test
    void aConditionIsKeyedByItsCodeAndTheDayItTookEffect() {
        session.add(new ConditionTextRow("T-02", LocalDate.of(2024, 1, 1), "travel", "Daylight hours only.", 20));
        session.add(new ConditionTextRow(
                "T-02", LocalDate.of(2025, 6, 1), "travel", "Daylight hours only, half an hour either side.", 20));
        session.commit();
        List<ConditionTextRow> rows = session.entityManager()
                .createQuery("select c from ConditionTextRow c where c.code = 'T-02'", ConditionTextRow.class)
                .getResultList();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).hasToString("<ConditionTextRow T-02 2024-01-01>");
    }

    @Test
    void theSameConditionOnTheSameDayTwiceIsRefused() {
        assertThatThrownBy(() -> {
                    for (int twice = 0; twice < 2; twice++) {
                        session.add(new ConditionTextRow(
                                "G-01", LocalDate.of(2024, 1, 1), "general", "Carry this permit."));
                    }
                    session.commit();
                })
                .isInstanceOf(IntegrityViolation.class);
    }

    @Test
    void aConditionWithNoWordsIsRefusedByTheDatabase() {
        session.add(new ConditionTextRow("G-09", MARCH, "general", ""));
        assertThatThrownBy(session::commit).isInstanceOf(IntegrityViolation.class);
    }
}
