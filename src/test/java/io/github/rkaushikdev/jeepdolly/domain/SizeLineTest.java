package io.github.rkaushikdev.jeepdolly.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.escorts.EscortRules;
import io.github.rkaushikdev.jeepdolly.limits.Thresholds;
import io.github.rkaushikdev.jeepdolly.travel.Windows;
import org.junit.jupiter.api.Test;

/** The line every size table draws, and the three tables that draw it. */
class SizeLineTest {

    private static final SizeLine WIDE = new SizeLine("width", Inches.of(12));

    @Test
    void aLoadIsPastTheLineWhenItIsOverTheSizeAndNotWhenItIsAtIt() {
        assertThat(WIDE.past(Envelope.read("12'1", "13'6", "80'"))).isTrue();
        assertThat(WIDE.past(Envelope.read("12'", "13'6", "80'"))).isFalse();
        assertThat(new SizeLine("rear_overhang", Inches.of(10)).past(Envelope.read("10'", "13'6", "80'", "0", "11'")))
                .isTrue();
    }

    @Test
    void theReasonIsTheMeasureAndTheSizeInThePermitsWords() {
        assertThat(new SizeLine("rear_overhang", Inches.of(10)).reason()).isEqualTo("rear overhang over 10 ft");
        assertThat(new SizeLine("height", Inches.of(14, 6))).hasToString("height over 14 ft 6 in");
        assertThat(WIDE.at(Inches.of(14))).isEqualTo(new SizeLine("width", Inches.of(14)));
    }

    @Test
    void aLineAboutNothingTheEnvelopeMeasuresOrAtNoSizeIsRefused() {
        assertThatThrownBy(() -> new SizeLine("weight", Inches.of(12)))
                .isInstanceOf(Impossible.class)
                .hasMessageContaining("weight");
        assertThatThrownBy(() -> new SizeLine("width", Inches.ZERO))
                .isInstanceOf(Impossible.class)
                .hasMessageContaining("past nothing");
    }

    @Test
    void theEscortTheWhenAndTheSuperloadTablesDrawTheSameLines() {
        assertThat(EscortRules.STANDARD.rows().get(0).line()).isEqualTo(WIDE);
        assertThat(Windows.RULES.get(0).line()).isEqualTo(WIDE);
        assertThat(Windows.RULES.get(0).drawn(true)).isEqualTo(WIDE.at(Inches.of(14)));
        assertThat(Thresholds.STANDARD.sizeLines())
                .containsExactly(
                        new SizeLine("width", Inches.of(16)),
                        new SizeLine("height", Inches.of(16)),
                        new SizeLine("length", Inches.of(150)));
    }
}
