package io.github.rkaushikdev.jeepdolly.cli;

import static io.github.rkaushikdev.jeepdolly.support.Consoles.call;
import static io.github.rkaushikdev.jeepdolly.support.Consoles.shown;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.jeepdolly.support.Consoles;
import io.github.rkaushikdev.jeepdolly.support.Consoles.Answer;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The line commands: the van's list and the counter's question. */
class CliLinesTest {

    private static final String HEIGHT = "19'";
    private static final String LIFT = "26'";
    private static final String MEASURED = "2024-09-12";

    private Console console;

    @BeforeEach
    void aConsoleOverASeededDatabase() {
        console = Consoles.over(Databases.seeded());
    }

    private Answer owner() {
        return call(
                console,
                "wires",
                "owner",
                "CMP",
                "Central Maine Power",
                "--telephone",
                "207 555 0100",
                "--notice",
                "5");
    }

    private Answer wire() {
        return wire("L-1", "US201-2", HEIGHT, LIFT, MEASURED);
    }

    /** A wire on file; a lift height or a survey day given as null is left off the command. */
    private Answer wire(String ident, String segment, String height, String liftTo, String measured) {
        List<String> arguments = new ArrayList<>(
                List.of("wires", "add", ident, segment, "CMP", "--place", "Fairfield village", "--height", height));
        if (liftTo != null) {
            arguments.addAll(List.of("--lift-to", liftTo));
        }
        if (measured != null) {
            arguments.addAll(List.of("--measured", measured));
        }
        return call(console, arguments.toArray(String[]::new));
    }

    private void seeded() {
        owner();
        wire();
    }

    private static Answer clearance(Console console, String height) {
        return call(
                console,
                "wires",
                "clearance",
                "waterville",
                "US201-1",
                "US201-2",
                "--height",
                height,
                "--travel",
                "2025-04-09",
                "--filed",
                "2025-03-01");
    }

    @Test
    void anOwnerCanBeFiledFromTheCommandLine() {
        Answer answer = owner();
        assertThat(answer.exitCode()).isZero();
        assertThat(answer.output().strip()).isEqualTo("filed  CMP  Central Maine Power");
    }

    @Test
    void theOwnersPrintWithTheirNoticeAndTheirWireCount() {
        seeded();
        List<String> printed = shown(call(console, "wires", "owners"));
        assertThat(printed).containsExactly("CMP  Central Maine Power  207 555 0100  5 days  1");
    }

    @Test
    void aWireCanBeFiledAndListed() {
        seeded();
        List<String> printed = shown(call(console, "wires", "list"));
        assertThat(printed).containsExactly("L-1  US201-2  Fairfield village  19 ft  26 ft  2024-09-12");
    }

    @Test
    void aWireNothingCanLiftSaysFixed() {
        owner();
        wire("L-1", "US201-2", HEIGHT, null, MEASURED);
        assertThat(call(console, "wires", "list").output()).contains("fixed");
    }

    @Test
    void aWireNobodyMeasuredSaysNever() {
        owner();
        wire("L-1", "US201-2", HEIGHT, LIFT, null);
        assertThat(call(console, "wires", "list").output()).contains("never");
    }

    @Test
    void aWireOverARoadNobodyHasIsARefusal() {
        owner();
        assertThat(wire("L-1", "XX-9", HEIGHT, LIFT, MEASURED).exitCode()).isNotZero();
    }

    @Test
    void aWireAgainstAnOwnerNobodyHasIsARefusal() {
        assertThat(wire().exitCode()).isNotZero();
    }

    @Test
    void theWiresOfOneRoadCanBeAskedFor() {
        seeded();
        wire("L-2", "US2-1", HEIGHT, LIFT, MEASURED);
        assertThat(shown(call(console, "wires", "list", "--segment", "US2-1"))).hasSize(1);
        assertThat(shown(call(console, "wires", "list"))).hasSize(2);
    }

    @Test
    void theLowWiresCanBeAskedForOnTheirOwn() {
        seeded();
        wire("L-2", "US2-1", "30'", LIFT, MEASURED);
        assertThat(shown(call(console, "wires", "list", "--under", "20'"))).hasSize(1);
    }

    @Test
    void remeasuringReplacesWhatTheCardSaid() {
        seeded();
        Answer answer = call(console, "wires", "measure", "L-1", "21'", "--on", "2025-10-02");
        assertThat(answer.output().strip()).isEqualTo("measured  L-1  21 ft  2025-10-02");
        assertThat(call(console, "wires", "list").output()).contains("21 ft");
    }

    @Test
    void remeasuringAWireNobodyHasIsARefusal() {
        assertThat(call(console, "wires", "measure", "L-9", "21'").exitCode()).isNotZero();
    }

    @Test
    void theSurveyListNamesTheLowWiresOutOfCycle() {
        owner();
        wire("L-1", "US201-2", HEIGHT, LIFT, "2015-06-01");
        List<String> printed = shown(call(console, "wires", "survey", "--day", "2025-05-01"));
        assertThat(printed.get(0)).isEqualTo("line survey 2025-05-01, wires under 20 ft");
        assertThat(printed.get(1)).startsWith("L-1  US201-2 at Fairfield village  19 ft  2015-06-01");
    }

    @Test
    void aWireWalkedInsideTheCycleIsNotOnTheList() {
        seeded();
        List<String> printed = shown(call(console, "wires", "survey", "--day", "2025-05-01"));
        assertThat(printed.get(1)).isEqualTo("nothing low is out of cycle");
    }

    @Test
    void aWireNobodyEverMeasuredIsAlwaysOnTheList() {
        owner();
        wire("L-1", "US201-2", HEIGHT, LIFT, null);
        assertThat(call(console, "wires", "survey", "--day", "2025-05-01").output())
                .contains("never");
    }

    @Test
    void theFixedWiresAreListedUnderTheirOwnHeading() {
        owner();
        wire("L-1", "US201-2", HEIGHT, null, MEASURED);
        String printed = call(console, "wires", "survey", "--day", "2025-05-01").output();
        assertThat(printed).contains("wires that cannot be lifted");
    }

    @Test
    void aTallWireOutOfCycleIsLeftOffTheLowList() {
        owner();
        wire("L-1", "US201-2", "30'", null, "2015-06-01");
        List<String> printed = shown(call(console, "wires", "survey", "--day", "2025-05-01"));
        assertThat(printed.get(1)).isEqualTo("nothing low is out of cycle");
    }

    @Test
    void theClearanceQuestionAnswersClearForAShortLoad() {
        seeded();
        assertThat(clearance(console, "13'6").output().strip()).isEqualTo("clear  13 ft 6 in");
    }

    @Test
    void theClearanceQuestionNamesTheLiftAndTheNumber() {
        seeded();
        assertThat(clearance(console, "20'").output().strip())
                .isEqualTo("lift  L-1  Central Maine Power  207 555 0100  2025-04-02");
    }

    @Test
    void theClearanceQuestionSaysWhatIsBlocked() {
        owner();
        wire("L-1", "US201-2", HEIGHT, null, MEASURED);
        assertThat(clearance(console, "20'").output()).startsWith("blocked  L-1");
    }
}
