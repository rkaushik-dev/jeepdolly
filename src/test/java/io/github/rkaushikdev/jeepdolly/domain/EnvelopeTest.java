package io.github.rkaushikdev.jeepdolly.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.support.Signatures;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The envelope, and the excess it shows against a limiting box. */
class EnvelopeTest {

    /** Legal size on a state highway, which is the box most loads are compared to. */
    private static final Envelope LEGAL =
            new Envelope(Inches.of(8, 6), Inches.of(13, 6), Inches.of(75), Inches.of(3), Inches.of(4));

    private static Envelope box() {
        return Envelope.read("12'", "13'6\"", "80'", "0", "0");
    }

    private static Envelope box(String front, String rear) {
        return Envelope.read("12'", "13'6\"", "80'", front, rear);
    }

    @Test
    void anEnvelopeReadsOffTheFormInWhateverTheHaulerWrote() {
        Envelope load = Envelope.read("12'6\"", "14-2", "92 ft", "24", "10'");
        assertThat(load.width()).isEqualTo(new Inches(150));
        assertThat(load.height()).isEqualTo(new Inches(170));
        assertThat(load.length()).isEqualTo(new Inches(1104));
        assertThat(load.frontOverhang()).isEqualTo(new Inches(24));
        assertThat(load.rearOverhang()).isEqualTo(new Inches(120));
    }

    @Test
    void anEnvelopePrintsTheWayThePermitFacePrintsIt() {
        assertThat(box()).hasToString("12 ft wide, 13 ft 6 in high, 80 ft long");
        assertThat(box("2'", "9'").toString()).endsWith("2 ft front and 9 ft rear overhang");
    }

    @Test
    void aLoadWithoutAWidthOrAHeightIsNotALoad() {
        assertThatThrownBy(() -> new Envelope(new Inches(0), Inches.of(13), Inches.of(70)))
                .isInstanceOf(Impossible.class);
        assertThatThrownBy(() -> new Envelope(Inches.of(8), new Inches(0), Inches.of(70)))
                .isInstanceOf(Impossible.class);
        assertThatThrownBy(() -> new Envelope(Inches.of(8), Inches.of(13), new Inches(0)))
                .isInstanceOf(Impossible.class);
    }

    @Test
    void anOverhangMeasuredInwardIsATypingMistake() {
        assertThatThrownBy(() -> new Envelope(Inches.of(8), Inches.of(13), Inches.of(70), new Inches(-12)))
                .isInstanceOf(Impossible.class);
    }

    @Test
    void overhangsLongerThanTheCombinationAreRefused() {
        assertThatThrownBy(() -> new Envelope(Inches.of(8), Inches.of(13), Inches.of(20), Inches.of(12), Inches.of(12)))
                .isInstanceOf(Impossible.class)
                .hasMessageContaining("longer than the whole combination");
    }

    @Test
    void aMeasureThatIsNotASizeIsRefusedBeforeTheArithmetic() {
        // Envelope(102, ...) was a type error at run time. Every way in takes sizes, so a bare
        // number does not compile; a missing size is refused before any arithmetic.
        Constructor<?>[] constructors = Envelope.class.getConstructors();
        assertThat(constructors).isNotEmpty();
        assertThat(Arrays.stream(constructors).flatMap(one -> Arrays.stream(one.getParameterTypes())))
                .containsOnly(Inches.class);
        Signatures.methodTakesOnly(Envelope.class, "within", Envelope.class);
        assertThatThrownBy(() -> new Envelope(null, Inches.of(13), Inches.of(70)))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("width is a size");
    }

    @Test
    void theOverhangsAddUpAndSayWhetherAnythingProjects() {
        assertThat(box("2'", "9'").overhang()).isEqualTo(new Inches(132));
        assertThat(box("2'", "9'").projects()).isTrue();
        assertThat(box().projects()).isFalse();
        assertThat(box().overhang()).isEqualTo(new Inches(0));
    }

    @Test
    void aLoadInsideTheLegalBoxShowsNoExcessAtAll() {
        Envelope legalLoad = Envelope.read("8'", "13'", "70'", "1'", "3'");
        Excess excess = legalLoad.excessOver(LEGAL);
        assertThat(excess.any()).isFalse();
        assertThat(excess.over()).isEmpty();
        assertThat(excess.worst()).isEmpty();
        assertThat(excess).hasToString("within limits");
        assertThat(legalLoad.within(LEGAL)).isTrue();
    }

    @Test
    void anExcessNamesEveryMeasureThatIsOverInFormOrder() {
        Envelope load = Envelope.read("14'", "15'", "110'", "6'", "20'");
        Excess excess = load.excessOver(LEGAL);
        assertThat(excess.over()).containsExactly("width", "height", "length", "front_overhang", "rear_overhang");
        assertThat(excess.width()).isEqualTo(new Inches(66));
        assertThat(excess.height()).isEqualTo(new Inches(18));
        assertThat(excess.length()).isEqualTo(new Inches(420));
        assertThat(load.within(LEGAL)).isFalse();
    }

    @Test
    void aMeasureInsideTheLimitContributesNothingToTheExcess() {
        Envelope load = Envelope.read("14'", "13'", "70'");
        Excess excess = load.excessOver(LEGAL);
        assertThat(excess.over()).containsExactly("width");
        assertThat(excess.height()).isEqualTo(new Inches(0));
        assertThat(excess).hasToString("width over by 5 ft 6 in");
    }

    @Test
    void theWorstMeasureIsTheOneTheRefusalLetterLeadsWith() {
        Envelope load = Envelope.read("9'", "16'", "70'");
        Excess.Over worst = load.excessOver(LEGAL).worst().orElseThrow();
        assertThat(worst.name()).isEqualTo("height");
        assertThat(worst.amount()).isEqualTo(new Inches(30));
    }

    @Test
    void anExcessPrintsEveryMeasureItHolds() {
        Excess excess = new Excess(new Inches(18), Inches.ZERO, Inches.ZERO, Inches.ZERO, new Inches(48));
        assertThat(excess).hasToString("width over by 1 ft 6 in, rear overhang over by 4 ft");
        assertThat(excess.any()).isTrue();
    }

    @Test
    void growingAnEnvelopeAnswersAWhatIfWithoutEditingTheFirstOne() {
        Envelope load = box();
        Map<String, Inches> by = new LinkedHashMap<>();
        by.put("height", new Inches(12));
        by.put("width", new Inches(6));
        Envelope taller = load.grown(by);
        assertThat(taller.height()).isEqualTo(new Inches(174));
        assertThat(taller.width()).isEqualTo(new Inches(150));
        assertThat(load.height()).isEqualTo(new Inches(162));
    }

    @Test
    void growingAMeasureThatDoesNotExistIsRefused() {
        assertThatThrownBy(() -> box().grown(Map.of("weight", new Inches(12)))).isInstanceOf(Impossible.class);
    }
}
