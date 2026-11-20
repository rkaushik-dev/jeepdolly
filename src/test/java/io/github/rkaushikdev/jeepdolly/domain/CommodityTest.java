package io.github.rkaushikdev.jeepdolly.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.jeepdolly.Conflict;
import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import org.junit.jupiter.api.Test;

/** The commodity table and the divisibility test it answers. */
class CommodityTest {

    @Test
    void aLoadThatCouldNotHaveBeenSplitCanBePermitted() {
        CommodityBook book = CommodityBook.standard();
        assertThat(book.get("EXCV").permittable()).isTrue();
        assertThat(book.get("EXCV").divisible()).isFalse();
        assertThat(book.get("EXCV").restrictedToAllowance()).isFalse();
    }

    @Test
    void aLoadThatCouldHaveBeenTwoTripsCannotBePermitted() {
        Commodity gravel = CommodityBook.standard().get("GRVL");
        assertThat(gravel.divisible()).isTrue();
        assertThat(gravel.permittable()).isFalse();
    }

    @Test
    void aDivisibleLoadWithAStandingAllowanceMovesAnyway() {
        Commodity wood = CommodityBook.standard().get("WOOD");
        assertThat(wood.divisible()).isTrue();
        assertThat(wood.permittable()).isTrue();
        assertThat(wood.restrictedToAllowance()).isTrue();
        assertThat(wood.allowance()).contains("forest products");
    }

    @Test
    void anAllowanceOnSomethingThatWasNeverDivisibleIsAMistake() {
        assertThatThrownBy(() -> Commodity.builder("BEAM", "steel beam", Family.STRUCTURAL)
                        .allowance("forest products")
                        .build())
                .isInstanceOf(Impossible.class)
                .hasMessageContaining("needs no allowance");
    }

    @Test
    void aCommodityCodeIsACodeAndNotASentence() {
        assertThatThrownBy(() -> new Commodity("excv", "excavator", Family.MACHINERY))
                .isInstanceOf(Impossible.class);
        assertThatThrownBy(() -> new Commodity("", "excavator", Family.MACHINERY))
                .isInstanceOf(Impossible.class);
    }

    @Test
    void aDescriptionTooLongForThePermitFaceIsRefused() {
        assertThatThrownBy(() -> new Commodity("EXCV", "e".repeat(61), Family.MACHINERY))
                .isInstanceOf(Impossible.class)
                .hasMessageContaining("will not fit");
    }

    @Test
    void aTipLimitThatIsNotAnAngleIsRefused() {
        assertThatThrownBy(() -> Commodity.builder("GIRD", "girder", Family.STRUCTURAL)
                        .tipLimitDegrees(0)
                        .build())
                .isInstanceOf(Impossible.class);
        assertThatThrownBy(() -> Commodity.builder("GIRD", "girder", Family.STRUCTURAL)
                        .tipLimitDegrees(90)
                        .build())
                .isInstanceOf(Impossible.class);
    }

    @Test
    void aCommodityPrintsAsThePermitFacePrintsIt() {
        assertThat(CommodityBook.standard().get("XFMR")).hasToString("XFMR power transformer");
        assertThat(Family.UTILITY).hasToString("utility");
    }

    @Test
    void theBookRefusesToFileOneCodeTwice() {
        CommodityBook book = new CommodityBook();
        book.add(new Commodity("EXCV", "excavator", Family.MACHINERY));
        assertThatThrownBy(() -> book.add(new Commodity("EXCV", "shovel", Family.MACHINERY)))
                .isInstanceOf(Conflict.class);
        assertThat(book.size()).isEqualTo(1);
    }

    @Test
    void askingForACommodityNobodyFiledSaysSoWithTheCode() {
        CommodityBook book = CommodityBook.standard();
        NotOnFile refusal = catchThrowableOfType(NotOnFile.class, () -> book.get("ZZZZ"));
        assertThat(refusal.facts().get("code")).isEqualTo("ZZZZ");
        assertThat(book.has("ZZZZ")).isFalse();
        assertThat(book.contains("ZZZZ")).isFalse();
    }

    @Test
    void theStandardTableHoldsTheLinesTheCounterUses() {
        CommodityBook book = CommodityBook.standard();
        assertThat(book.size()).isEqualTo(17);
        assertThat(book.contains("EXCV")).isTrue();
        assertThat(book.codes().get(0)).isEqualTo("BEAM");
    }

    @Test
    void aFamilyComesBackSortedSoAReportReadsTheSameTwice() {
        assertThat(CommodityBook.standard().inFamily(Family.FOREST))
                .extracting(Commodity::code)
                .containsExactly("CHIP", "WOOD");
        assertThat(CommodityBook.standard().inFamily(Family.MACHINERY).get(0).code())
                .isEqualTo("CRNE");
    }

    @Test
    void theCommoditiesUnderOneAllowanceCanBeListed() {
        CommodityBook book = CommodityBook.standard();
        assertThat(book.underAllowance("forest products"))
                .extracting(Commodity::code)
                .containsExactly("WOOD");
        assertThat(book.underAllowance("forage")).extracting(Commodity::code).containsExactly("HAYB");
        assertThat(book.underAllowance("nothing")).isEmpty();
    }

    @Test
    void theFlagsThatLaterRulesReadAreOnTheRecord() {
        CommodityBook book = CommodityBook.standard();
        assertThat(book.get("XFMR").tipLimitDegrees()).contains(3);
        assertThat(book.get("BOAT").highCentre()).isTrue();
        assertThat(book.get("BEAM").highCentre()).isFalse();
        assertThat(book.get("BEAM").tipLimitDegrees()).isEmpty();
    }

    @Test
    void aPieceNamesItselfAndCarriesASerialWhenThereIsOne() {
        CommodityBook book = CommodityBook.standard();
        Piece piece = new Piece(book.get("EXCV"), "Cat 336 excavator", "TMD01297");
        assertThat(piece).hasToString("Cat 336 excavator (TMD01297)");
        assertThat(new Piece(book.get("BEAM"), "W36x300 girder")).hasToString("W36x300 girder");
    }

    @Test
    void aPieceWithNoDescriptionIsNotOnThePermit() {
        assertThatThrownBy(() -> new Piece(CommodityBook.standard().get("EXCV"), "   "))
                .isInstanceOf(Impossible.class);
    }
}
