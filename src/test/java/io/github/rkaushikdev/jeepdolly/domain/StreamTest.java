package io.github.rkaushikdev.jeepdolly.domain;

import static io.github.rkaushikdev.jeepdolly.support.Streams.jeepedLowboy;
import static io.github.rkaushikdev.jeepdolly.support.Streams.make;
import static io.github.rkaushikdev.jeepdolly.support.Streams.tractorLowboy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.Pounds;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The axle stream, its groups, and the runs the bridge formula wants. */
class StreamTest {

    @Test
    void aStreamAddsUpToTheNumberOnTheScaleTicket() {
        AxleStream stream = tractorLowboy();
        assertThat(stream.count()).isEqualTo(6);
        assertThat(stream.size()).isEqualTo(6);
        assertThat(stream.gross()).isEqualTo(new Pounds(94000));
        assertThat(stream.extent()).isEqualTo(new Inches(538));
    }

    @Test
    void aStreamPrintsItselfForTheWorksheet() {
        assertThat(tractorLowboy()).hasToString("AxleStream(6 axles, 94,000 lb, 44 ft 10 in)");
    }

    @Test
    void aStreamNeedsOneFewerSpacingThanItHasAxles() {
        assertThatThrownBy(() -> make(List.of(12000, 17000, 17000), List.of(200)))
                .isInstanceOf(Impossible.class)
                .hasMessageContaining("need 2 spacings");
    }

    @Test
    void twoAxlesCannotStandInTheSamePlace() {
        assertThatThrownBy(() -> make(List.of(12000, 17000), List.of(0))).isInstanceOf(Impossible.class);
    }

    @Test
    void aStreamWithNoAxlesIsRefused() {
        assertThatThrownBy(() -> new AxleStream(List.of(), List.of())).isInstanceOf(Impossible.class);
    }

    @Test
    void theDistanceBetweenTwoAxlesIsTheSpacingsBetweenThem() {
        AxleStream stream = tractorLowboy();
        assertThat(stream.distance(0, 1)).isEqualTo(new Inches(200));
        assertThat(stream.distance(1, 3)).isEqualTo(new Inches(234));
        assertThat(stream.distance(3, 5)).isEqualTo(new Inches(104));
        assertThat(stream.distance(2, 2)).isEqualTo(new Inches(0));
    }

    @Test
    void askingAboutAnAxleThatIsNotThereIsOutOfBounds() {
        AxleStream stream = tractorLowboy();
        assertThatThrownBy(() -> stream.distance(0, 6)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> stream.distance(4, 2)).isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void theOffsetsPlaceEveryAxleFromTheFrontOne() {
        assertThat(tractorLowboy().offsets())
                .containsExactly(
                        new Inches(0),
                        new Inches(200),
                        new Inches(254),
                        new Inches(434),
                        new Inches(486),
                        new Inches(538));
    }

    @Test
    void theStreamSplitsIntoTheGroupsTheStatuteWritesLimitsFor() {
        List<AxleGroup> groups = tractorLowboy().groups();
        assertThat(groups).extracting(AxleGroup::name).containsExactly("single", "tandem", "tridem");
        assertThat(groups)
                .extracting(AxleGroup::weight)
                .containsExactly(new Pounds(12000), new Pounds(34000), new Pounds(48000));
        assertThat(groups)
                .extracting(AxleGroup::extent)
                .containsExactly(new Inches(0), new Inches(54), new Inches(104));
    }

    @Test
    void aJeepAndTheDrivesReadAsOneGroupWhenTheyStandClose() {
        List<AxleGroup> groups = jeepedLowboy().groups();
        assertThat(groups).extracting(AxleGroup::name).containsExactly("single", "quad", "tridem", "single");
        assertThat(groups.get(1).weight()).isEqualTo(new Pounds(75000));
        assertThat(groups.get(1).extent()).isEqualTo(new Inches(176));
    }

    @Test
    void aGapOfExactlyEightFeetIsStillOneGroup() {
        AxleStream stream = make(List.of(16000, 16000, 16000), List.of(96, 97));
        assertThat(stream.groups()).extracting(AxleGroup::count).containsExactly(2, 1);
    }

    @Test
    void everyRunOfTwoOrMoreAxlesIsOfferedToTheFormula() {
        List<Span> spans = tractorLowboy().spans();
        assertThat(spans).hasSize(15);
        assertThat(spans.get(0).first()).isEqualTo(0);
        assertThat(spans.get(0).last()).isEqualTo(1);
        assertThat(spans.get(0).weight()).isEqualTo(new Pounds(29000));
        assertThat(spans.get(0).extent()).isEqualTo(new Inches(200));
        assertThat(spans.get(spans.size() - 1).count()).isEqualTo(6);
        assertThat(spans.get(spans.size() - 1).weight()).isEqualTo(new Pounds(94000));
    }

    @Test
    void theRunsComeBackShortestFirstAndThenLeftToRight() {
        List<Span> spans = tractorLowboy().spans();
        assertThat(spans).extracting(Span::count).containsExactly(2, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 5, 5, 6);
        assertThat(spans.subList(0, 5)).extracting(Span::first).containsExactly(0, 1, 2, 3, 4);
    }

    @Test
    void askingForSingleAxleRunsIncludesEveryAxleOnItsOwn() {
        List<Span> spans = tractorLowboy().spans(1);
        assertThat(spans).hasSize(21);
        assertThat(spans.get(0).count()).isEqualTo(1);
        assertThat(spans.get(0).extent()).isEqualTo(new Inches(0));
    }

    @Test
    void aRunOfNoAxlesIsNotAThingToAskFor() {
        assertThatThrownBy(() -> tractorLowboy().spans(0)).isInstanceOf(Impossible.class);
    }

    @Test
    void aRunNamesItselfTheWayARefusalLetterDoes() {
        Span span = tractorLowboy().spans().stream()
                .filter(one -> one.first() == 3 && one.last() == 5)
                .findFirst()
                .orElseThrow();
        assertThat(span).hasToString("axles 4 to 6, 48,000 lb over 8 ft 8 in");
        assertThat(span.name()).isEqualTo("tridem");
    }

    @Test
    void theHeaviestAxleAndTheLiftableCountComeOffTheStream() {
        AxleStream stream = jeepedLowboy();
        assertThat(stream.heaviestAxle().weight()).isEqualTo(new Pounds(20000));
        assertThat(stream.liftableCount()).isEqualTo(0);
        assertThat(stream.gross()).isEqualTo(new Pounds(165000));
        assertThat(stream.extent()).isEqualTo(new Inches(780));
    }
}
