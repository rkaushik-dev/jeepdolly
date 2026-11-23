package io.github.rkaushikdev.jeepdolly.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.Unreadable;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tire sizes off the sidewall, and the whole inches of rubber the rule is allowed to count. */
class TireSizeTest {

    @Test
    void anInchSizeIsTheNumberBeforeTheR() {
        assertThat(TireSize.parse("11R22.5").width()).isEqualTo(new Inches(11));
        assertThat(TireSize.parse("11r24.5").width()).isEqualTo(new Inches(11));
        assertThat(TireSize.parse("11.00-20").width()).isEqualTo(new Inches(11));
        assertThat(TireSize.parse("15 R 22.5").width()).isEqualTo(new Inches(15));
    }

    @Test
    void aMetricSizeIsTheSectionWidthReadDownToTheWholeInch() {
        assertThat(TireSize.parse("425/65R22.5").width()).isEqualTo(new Inches(16));
        assertThat(TireSize.parse("445/50R22.5").width()).isEqualTo(new Inches(17));
        assertThat(TireSize.parse("275/80R22.5").width()).isEqualTo(new Inches(10));
        assertThat(TireSize.parse("255/70 R 22.5").width()).isEqualTo(new Inches(10));
        assertThat(TireSize.parse("385/65R22.5")).hasToString("385/65R22.5");
    }

    @Test
    void theApplicationMayGiveASidewallSizeOrAPlainWidth() {
        assertThat(TireSize.widthOf("11R22.5")).isEqualTo(new Inches(11));
        assertThat(TireSize.widthOf("425/65R22.5")).isEqualTo(new Inches(16));
        assertThat(TireSize.widthOf("11")).isEqualTo(new Inches(11));
        assertThat(TireSize.widthOf("16 in")).isEqualTo(new Inches(16));
    }

    @Test
    void aSizeThatIsNotOnAnySidewallIsRefused() {
        for (String written : List.of("big ones", "425/65", "R22.5", "4250/65R22.5", "11R")) {
            assertThatThrownBy(() -> TireSize.parse(written)).as(written).isInstanceOf(Unreadable.class);
        }
        assertThatThrownBy(() -> TireSize.widthOf("super singles"))
                .isInstanceOf(Unreadable.class)
                .hasMessageContaining("as a size");
    }
}
