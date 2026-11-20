package io.github.rkaushikdev.jeepdolly.routing;

import static io.github.rkaushikdev.jeepdolly.support.RoadLog.roadLog;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** The swept path rule: what a load wants, and where it comes up short. */
class SweepTest {

    private static final List<String> ACROSS = List.of("US201-1", "US201-2", "SA148-2", "SR43-1");

    private static Envelope load() {
        return load("75'", "0");
    }

    private static Envelope load(String length) {
        return load(length, "0");
    }

    private static Envelope load(String length, String rear) {
        return Envelope.read("12'", "13'6", length, "0", rear);
    }

    private static TurnBook book() {
        return book(40);
    }

    private static TurnBook book(int radius) {
        TurnBook made = new TurnBook();
        made.add(new Corner("skowhegan", "US201-2", "SA148-2", radius, "granite kerb and a bank"));
        made.add(new Corner("canaan", "SA148-2", "SR43-1", 60));
        return made;
    }

    private static Itinerary trip() {
        return trip(ACROSS, "waterville");
    }

    private static Itinerary trip(List<String> idents, String origin) {
        return new Itinerary(roadLog(), origin, idents);
    }

    @Test
    void aPlainLengthWantsThePlainRadius() {
        assertThat(Sweep.radiusWanted(load("75'"))).isEqualTo(40);
        assertThat(Sweep.radiusWanted(load("70'"))).isEqualTo(40);
    }

    @Test
    void everyFootPastThePlainLengthWantsAnotherFootOfRadius() {
        assertThat(Sweep.radiusWanted(load("95'"))).isEqualTo(60);
    }

    @Test
    void aRearOverhangSwingsWideAndWantsTwoFeetForOne() {
        assertThat(Sweep.radiusWanted(load("75'", "10'"))).isEqualTo(60);
        assertThat(Sweep.radiusWanted(load("95'", "10'"))).isEqualTo(80);
    }

    @Test
    void onlyTheCornersTheTripActuallyTurnsAtAreRead() {
        List<Corner> found = Sweep.cornersOn(trip(), book());
        assertThat(found).extracting(Corner::node).containsExactly("skowhegan", "canaan");
        List<Corner> straight = Sweep.cornersOn(trip(List.of("US201-1", "US201-2"), "waterville"), book());
        assertThat(straight).isEmpty();
    }

    @Test
    void aShortLoadGetsRoundEverythingOnTheRoute() {
        assertThat(Sweep.tooTight(trip(), load(), book())).isEmpty();
    }

    @Test
    void aLongLoadIsStoppedAtTheCornerThatIsTooTight() {
        List<TooTight> found = Sweep.tooTight(trip(), load("95'"), book());
        assertThat(found).extracting(one -> one.corner().node()).containsExactly("skowhegan");
        assertThat(found.get(0).wanted()).isEqualTo(60);
        assertThat(found.get(0).shortBy()).isEqualTo(20);
        assertThat(found.get(0).toString()).contains("this load wants 60 ft");
    }

    @Test
    void aLoadLongEnoughIsStoppedAtEveryCorner() {
        List<TooTight> found = Sweep.tooTight(trip(), load("140'"), book());
        assertThat(found).extracting(one -> one.corner().node()).containsExactly("skowhegan", "canaan");
    }

    @Test
    void aCornerNobodyMeasuredIsNotInTheWayOfAnything() {
        TurnBook empty = new TurnBook();
        assertThat(Sweep.tooTight(trip(), load("200'"), empty)).isEmpty();
    }

    @Test
    void aCornerThatClearsByPlentyIsNotWorthACondition() {
        assertThat(Sweep.closeRun(trip(), load(), book(80))).isEmpty();
    }

    @Test
    void aCornerThatClearsByALittleComesBackAsACloseRun() {
        List<CloseRun> found = Sweep.closeRun(trip(), load(), book(46));
        assertThat(found).extracting(one -> one.corner().node()).containsExactly("skowhegan");
        assertThat(found.get(0).wanted()).isEqualTo(40);
        assertThat(found.get(0).spare()).isEqualTo(6);
        assertThat(found.get(0).toString()).contains("clears by 6 ft");
    }

    @Test
    void aCornerTheLoadWillNotGetRoundIsNotACloseRun() {
        List<CloseRun> found = Sweep.closeRun(trip(), load("95'"), book());
        assertThat(found).extracting(one -> one.corner().node()).containsExactly("canaan");
    }

    @Test
    void theMarginIsAnArgumentAndAWiderOneCatchesMore() {
        List<CloseRun> plain = Sweep.closeRun(trip(), load("95'"), book(80));
        assertThat(plain).extracting(one -> one.corner().node()).containsExactly("canaan");
        List<CloseRun> wide = Sweep.closeRun(trip(), load("95'"), book(80), 25);
        assertThat(wide).extracting(one -> one.corner().node()).containsExactly("skowhegan", "canaan");
    }

    @Test
    void theCloseRunAndTheRefusalNeverNameTheSameCorner() {
        Envelope envelope = load("95'");
        Set<String> tight = Sweep.tooTight(trip(), envelope, book()).stream()
                .map(one -> one.corner().node())
                .collect(Collectors.toSet());
        Set<String> near = Sweep.closeRun(trip(), envelope, book()).stream()
                .map(one -> one.corner().node())
                .collect(Collectors.toSet());
        Set<String> both = new HashSet<>(tight);
        both.retainAll(near);
        assertThat(both).isEmpty();
    }

    @Test
    void theWantedRadiusIsReadOffTheLoadAndNothingElse() {
        assertThat(Sweep.radiusWanted(load())).isEqualTo(Sweep.radiusWanted(Envelope.read("8'6", "10'", "75'")));
    }

    @Test
    void aFrontOverhangDoesNotSwingTheWayARearOneDoes() {
        Envelope front = Envelope.read("12'", "13'6", "75'", "10'", "0");
        assertThat(Sweep.radiusWanted(front)).isEqualTo(Sweep.radiusWanted(load()));
    }

    @Test
    void aCornerThatIsExactlyWhatTheLoadWantsIsACloseRunNotARefusal() {
        TurnBook exact = book(Sweep.radiusWanted(load()));
        assertThat(Sweep.tooTight(trip(), load(), exact)).isEmpty();
        assertThat(Sweep.closeRun(trip(), load(), exact))
                .extracting(one -> one.corner().node())
                .containsExactly("skowhegan");
    }

    @Test
    void theCornersOnARouteComeBackInTheOrderTheyAreDriven() {
        List<Corner> found = Sweep.cornersOn(trip(), book());
        assertThat(found).extracting(Corner::node).containsExactly("skowhegan", "canaan");
        List<Corner> backwards = Sweep.cornersOn(trip(List.of("SR43-1", "SA148-2"), "pittsfield"), book());
        assertThat(backwards).extracting(Corner::node).containsExactly("canaan");
    }
}
