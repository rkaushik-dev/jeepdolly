package io.github.rkaushikdev.jeepdolly.escorts;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import io.github.rkaushikdev.jeepdolly.domain.HighwayClass;
import io.github.rkaushikdev.jeepdolly.travel.TravelLimits;
import io.github.rkaushikdev.jeepdolly.travel.Windows;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Banners, flags, lamps and beacons, and what the dark changes. */
class MarkingTest {

    private static Envelope load(String width, String length, String front, String rear) {
        return Envelope.read(width, "13'6", length, front, rear);
    }

    private static Envelope load() {
        return load("8'", "70'", "0", "0");
    }

    private static Envelope wide(String width) {
        return load(width, "70'", "0", "0");
    }

    @Test
    void aLegalLoadWearsNothing() {
        Marking marking = Marking.forLoad(load());
        assertThat(marking.any()).isFalse();
        assertThat(marking).hasToString("no marking");
        assertThat(marking.conditions()).isEmpty();
    }

    @Test
    void aWideLoadWearsTwoBannersTwoFlagsAndABeacon() {
        Marking marking = Marking.forLoad(wide("12'"));
        assertThat(marking.banners()).isEqualTo(2);
        assertThat(marking.flags()).isEqualTo(2);
        assertThat(marking.beacons()).isEqualTo(1);
        assertThat(marking.lamps()).isEqualTo(0);
        assertThat(marking).hasToString("2 oversize load banners, 2 flags, 1 beacons");
    }

    @Test
    void aLongLoadWearsBannersButNoFlagsForTheLengthAlone() {
        Marking marking = Marking.forLoad(load("8'", "90'", "0", "0"));
        assertThat(marking.banners()).isEqualTo(2);
        assertThat(marking.flags()).isEqualTo(0);
        assertThat(marking.reasons()).containsExactly("over legal length");
    }

    @Test
    void aProjectionGetsItsOwnPairOfFlagsAtEachEnd() {
        Marking marking = Marking.forLoad(load("12'", "70'", "6'", "14'"));
        assertThat(marking.flags()).isEqualTo(6);
        assertThat(marking.reasons()).containsExactly("over legal width", "front overhang", "rear overhang");
    }

    @Test
    void aShortProjectionIsNotFlagged() {
        Marking marking = Marking.forLoad(load("12'", "70'", "0", "3'"));
        assertThat(marking.flags()).isEqualTo(2);
    }

    @Test
    void afterDarkTheFlagsBecomeLampsRatherThanJoiningThem() {
        Marking marking = Marking.forLoad(load("12'", "70'", "0", "14'"), true, false);
        assertThat(marking.flags()).isEqualTo(0);
        assertThat(marking.lamps()).isEqualTo(4);
        assertThat(marking.afterDark()).isTrue();
        assertThat(Marking.forLoad(wide("12'")).afterDark()).isFalse();
    }

    @Test
    void aSuperloadCarriesTwoBeacons() {
        Marking marking = Marking.forLoad(wide("18'"), false, true);
        assertThat(marking.beacons()).isEqualTo(2);
        assertThat(marking.reasons()).contains("superload");
    }

    @Test
    void aLegalSizeSuperloadStillWearsNoBanner() {
        Marking marking = Marking.forLoad(load(), false, true);
        assertThat(marking.banners()).isEqualTo(0);
        assertThat(marking.beacons()).isEqualTo(0);
    }

    @Test
    void theConditionsSayTheSizesTheEnforcementPeopleMeasure() {
        List<String> printed = Marking.forLoad(load("12'", "70'", "0", "14'")).conditions();
        assertThat(printed.get(0)).contains("seven feet by eighteen inches");
        assertThat(printed.get(1)).startsWith("4 eighteen inch red flags");
        assertThat(printed.get(2)).endsWith("visible from the rear");
    }

    @Test
    void theNightConditionsNameLampsAndNotFlags() {
        List<String> printed = Marking.forLoad(wide("12'"), true, false).conditions();
        assertThat(printed).noneMatch(one -> one.contains("red flags"));
        assertThat(printed).anyMatch(one -> one.contains("amber lamps in place of flags"));
    }

    @Test
    void aLoadHeldToDaylightWearsItsFlagsAndNoLamps() {
        Envelope load = wide("13'");
        Marking marking = Marking.forTrip(load, Windows.limitsFor(load), false);
        assertThat(marking.flags()).isEqualTo(2);
        assertThat(marking.lamps()).isEqualTo(0);
        assertThat(marking.roundTheClock()).isFalse();
        assertThat(marking).isEqualTo(Marking.forLoad(load));
    }

    @Test
    void aLoadThatMayRunAtNightWearsTheFlagsAndCarriesTheLamps() {
        Envelope load = load("8'", "90'", "0", "6'");
        Marking marking = Marking.forTrip(load, Windows.limitsFor(load), false);
        assertThat(marking.flags()).isEqualTo(2);
        assertThat(marking.lamps()).isEqualTo(2);
        assertThat(marking.roundTheClock()).isTrue();
        assertThat(marking.afterDark()).isFalse();
        assertThat(marking.redLamps()).isEqualTo(2);
        assertThat(marking).hasToString("2 oversize load banners, 2 flags, 2 lamps, 2 red lamps, 1 beacons");
        assertThat(marking.conditions())
                .contains("2 amber lamps at the same places, lit from sunset to sunrise")
                .noneMatch(one -> one.contains("in place of flags"));
    }

    @Test
    void theInterstateWidthLineLeavesAWideLoadOnTheRoadAfterDark() {
        Envelope load = wide("13'");
        TravelLimits interstate = Windows.limitsFor(load, false, false, List.of(HighwayClass.INTERSTATE));
        Marking marking = Marking.forTrip(load, interstate, false);
        assertThat(marking.flags()).isEqualTo(2);
        assertThat(marking.lamps()).isEqualTo(2);
        assertThat(Marking.forTrip(load, interstate, true).beacons()).isEqualTo(2);
    }

    @Test
    void afterDarkTheEndOfARearOverhangCarriesRedAsWellAsTheAmberCorners() {
        Marking marking = Marking.forLoad(load("12'", "70'", "0", "14'"), true, false);
        assertThat(marking.redLamps()).isEqualTo(2);
        assertThat(marking.lamps()).isEqualTo(4);
        assertThat(marking.count(Marking.Worn.RED_LAMPS)).isEqualTo(2);
        assertThat(marking).hasToString("2 oversize load banners, 4 lamps, 2 red lamps, 1 beacons");
        assertThat(marking.conditions())
                .containsExactly(
                        "2 OVERSIZE LOAD banners, one front and one rear, seven feet by eighteen inches",
                        "4 amber lamps in place of flags, lit for the whole move",
                        "2 red lamps at the extreme end of the rear overhang, lit after dark",
                        "1 amber beacons on the power unit, visible from the rear");
    }

    @Test
    void byDayAndForAFrontOverhangThereIsNoRedLamp() {
        assertThat(Marking.forLoad(load("12'", "70'", "0", "14'")).redLamps()).isEqualTo(0);
        assertThat(Marking.forLoad(load("12'", "70'", "6'", "0"), true, false).redLamps())
                .isEqualTo(0);
        assertThat(Marking.forLoad(load("12'", "70'", "0", "4'"), true, false).redLamps())
                .isEqualTo(0);
    }

    @Test
    void aMarkingWrittenByCountWearsOnlyWhatItCounts() {
        Marking marking = new Marking(2, 0, 0, 1);
        assertThat(marking.worn()).containsOnlyKeys(Marking.Worn.BANNERS, Marking.Worn.BEACONS);
        assertThat(marking).isEqualTo(new Marking(Map.of(Marking.Worn.BANNERS, 2, Marking.Worn.BEACONS, 1), List.of()));
        assertThat(new Marking(0, 0, 0, 0).any()).isFalse();
        assertThat(new Marking(0, 0, 0, 0).conditions()).isEmpty();
    }
}
