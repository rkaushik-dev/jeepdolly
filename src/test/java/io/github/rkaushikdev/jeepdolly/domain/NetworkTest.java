package io.github.rkaushikdev.jeepdolly.domain;

import static io.github.rkaushikdev.jeepdolly.support.RoadLog.place;
import static io.github.rkaushikdev.jeepdolly.support.RoadLog.roadLog;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.jeepdolly.Conflict;
import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Miles;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import org.junit.jupiter.api.Test;

/** The road log: segments, the nodes they join, and what leaves a place. */
class NetworkTest {

    private static Segment segment(String ident, String start, String end, int length, boolean oneWay) {
        return new Segment(ident, Route.parse("US 2"), start, end, new Miles(length), place(start), oneWay);
    }

    private static Segment segment(String ident, String start, String end) {
        return segment(ident, start, end, 620, false);
    }

    @Test
    void theRoadLogHoldsEverySegmentAndFindsOneByItsIdent() {
        Network network = roadLog();
        assertThat(network.size()).isEqualTo(12);
        assertThat(network.contains("US201-2")).isTrue();
        Segment piece = network.segment("US201-2");
        assertThat(piece.start()).isEqualTo("fairfield");
        assertThat(piece.length()).isEqualTo(Miles.of(12, 40));
        assertThat(piece.highwayClass()).isSameAs(HighwayClass.ARTERIAL);
    }

    @Test
    void aSegmentPrintsTheWayTheRoadLogListsIt() {
        assertThat(roadLog().segment("SA148-1")).hasToString("SA148-1 SA 148 norridgewock to canaan 9.40 mi");
    }

    @Test
    void aSegmentThatStartsWhereItEndsIsATypingMistake() {
        assertThatThrownBy(() -> segment("X-1", "skowhegan", "skowhegan")).isInstanceOf(Impossible.class);
    }

    @Test
    void aSegmentWithNoLengthIsNotASegment() {
        assertThatThrownBy(() -> segment("X-1", "skowhegan", "norridgewock", 0, false))
                .isInstanceOf(Impossible.class);
    }

    @Test
    void aNodeIsNamedOrItIsNotANode() {
        assertThatThrownBy(() -> new Node("  ", "Skowhegan")).isInstanceOf(Impossible.class);
    }

    @Test
    void theOtherEndOfASegmentIsAskedForFromEitherEnd() {
        Segment piece = roadLog().segment("US201-2");
        assertThat(piece.otherEnd("fairfield")).isEqualTo("skowhegan");
        assertThat(piece.otherEnd("skowhegan")).isEqualTo("fairfield");
        assertThatThrownBy(() -> piece.otherEnd("newport")).isInstanceOf(Impossible.class);
    }

    @Test
    void everythingLeavingAPlaceComesBackInOneList() {
        assertThat(roadLog().leaving("skowhegan"))
                .extracting(Segment::ident)
                .containsExactly("SA148-2", "US2-1", "US201-2", "US201-3");
    }

    @Test
    void aOneWaySegmentLeavesOnlyFromTheEndItStartsAt() {
        Network network = roadLog();
        network.addSegment(segment("RAMP-1", "pittsfield", "newport", 620, true));
        assertThat(network.leaving("pittsfield")).extracting(Segment::ident).contains("RAMP-1");
        assertThat(network.leaving("newport")).extracting(Segment::ident).doesNotContain("RAMP-1");
    }

    @Test
    void theSegmentsJoiningTwoPlacesAreFoundInEitherDirection() {
        Network network = roadLog();
        assertThat(network.between("waterville", "fairfield"))
                .extracting(Segment::ident)
                .containsExactly("I95-1", "US201-1");
        assertThat(network.between("fairfield", "waterville"))
                .extracting(Segment::ident)
                .containsExactly("I95-1", "US201-1");
        assertThat(network.between("waterville", "anson")).isEmpty();
    }

    @Test
    void theSegmentsOfOneRouteComeBackInRoadLogOrder() {
        assertThat(roadLog().onRoute(Route.parse("US 201")))
                .extracting(Segment::ident)
                .containsExactly("US201-1", "US201-2", "US201-3");
    }

    @Test
    void theSegmentsOfOneDistrictAreWhatAThawOrderReaches() {
        assertThat(roadLog().inDistrict(4))
                .hasSize(7)
                .allSatisfy(one -> assertThat(one.jurisdiction().district()).isEqualTo(4));
    }

    @Test
    void aSegmentNamingANodeNobodyFiledIsRefused() {
        Network network = roadLog();
        assertThatThrownBy(() -> network.addSegment(segment("X-9", "skowhegan", "bangor")))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("bangor");
    }

    @Test
    void theSameSegmentOrNodeMayNotBeFiledTwice() {
        Network network = roadLog();
        assertThatThrownBy(() -> network.addSegment(segment("US2-1", "skowhegan", "norridgewock")))
                .isInstanceOf(Conflict.class);
        assertThatThrownBy(() -> network.addNode(new Node("skowhegan", "Skowhegan")))
                .isInstanceOf(Conflict.class);
    }

    @Test
    void askingAboutAPlaceThatIsNotOnFileSaysWhichOne() {
        Network network = roadLog();
        NotOnFile refusal = catchThrowableOfType(NotOnFile.class, () -> network.leaving("bangor"));
        assertThat(refusal.facts().get("node")).isEqualTo("bangor");
        assertThatThrownBy(() -> network.segment("NOPE-1")).isInstanceOf(NotOnFile.class);
    }

    @Test
    void theLogListsItsNodesAndSegmentsInAStableOrder() {
        Network network = roadLog();
        assertThat(network.nodes().subList(0, 3))
                .extracting(Node::name)
                .containsExactly("anson", "canaan", "fairfield");
        assertThat(network.segments().get(0).ident()).isEqualTo("I95-1");
    }

    @Test
    void aTownWayIsOnTheLogEvenThoughNoPermitNamesIt() {
        Segment piece = roadLog().segment("TR104-1");
        assertThat(piece.highwayClass()).isSameAs(HighwayClass.TOWN_WAY);
        assertThat(piece.highwayClass().permittable()).isFalse();
    }

    @Test
    void anEmptyNetworkCanBeBuiltUpAPieceAtATime() {
        Network network = new Network();
        network.addNode(new Node("skowhegan", "Skowhegan"));
        network.addNode(new Node("norridgewock", "Norridgewock"));
        network.addSegment(new Segment(
                "US2-1",
                Route.parse("US 2"),
                "skowhegan",
                "norridgewock",
                new Miles(620),
                new Jurisdiction("Somerset", "Skowhegan", 4)));
        assertThat(network.size()).isEqualTo(1);
        assertThat(network.node("skowhegan").town()).isEqualTo("Skowhegan");
    }
}
