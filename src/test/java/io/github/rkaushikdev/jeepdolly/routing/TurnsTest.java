package io.github.rkaushikdev.jeepdolly.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.jeepdolly.Conflict;
import io.github.rkaushikdev.jeepdolly.Impossible;
import org.junit.jupiter.api.Test;

/** The card index of corners, and what it will and will not file. */
class TurnsTest {

    private static TurnBook book() {
        return book(40);
    }

    private static TurnBook book(int radius) {
        TurnBook made = new TurnBook();
        made.add(new Corner("skowhegan", "US201-2", "SA148-2", radius, "granite kerb and a bank"));
        made.add(new Corner("canaan", "SA148-2", "SR43-1", 60));
        return made;
    }

    @Test
    void aCornerIsTheSameCornerDrivenEitherWay() {
        Corner corner = new Corner("skowhegan", "US201-2", "SA148-2", 40);
        assertThat(corner.joins("US201-2", "SA148-2")).isTrue();
        assertThat(corner.joins("SA148-2", "US201-2")).isTrue();
        assertThat(corner.joins("US201-2", "US2-1")).isFalse();
    }

    @Test
    void aCornerWithNoRadiusOrOneSegmentIsRefused() {
        assertThatThrownBy(() -> new Corner("skowhegan", "US201-2", "SA148-2", 0))
                .isInstanceOf(Impossible.class);
        assertThatThrownBy(() -> new Corner("skowhegan", "US201-2", "US201-2", 40))
                .isInstanceOf(Impossible.class);
    }

    @Test
    void aCornerPrintsTheWayTheCardIndexReads() {
        Corner corner = new Corner("skowhegan", "US201-2", "SA148-2", 40, "granite kerb");
        assertThat(corner).hasToString("skowhegan US201-2 to SA148-2, 40 ft: granite kerb");
    }

    @Test
    void theSameCornerIsNotFiledTwiceEitherWayRound() {
        TurnBook made = book();
        assertThatThrownBy(() -> made.add(new Corner("skowhegan", "SA148-2", "US201-2", 45)))
                .isInstanceOf(Conflict.class);
        assertThat(made.size()).isEqualTo(2);
    }

    @Test
    void theBookAnswersByNodeAndByTheTwoSegments() {
        TurnBook made = book();
        assertThat(made.between("skowhegan", "US201-2", "SA148-2").orElseThrow().radiusFeet())
                .isEqualTo(40);
        assertThat(made.between("skowhegan", "US201-2", "US2-1")).isEmpty();
        assertThat(made.at("skowhegan")).hasSize(1);
    }

    @Test
    void theTightCornersAreTheListTheOfficeKeepsOnTheWall() {
        TurnBook made = book(32);
        assertThat(made.tight()).extracting(Corner::node).containsExactly("skowhegan");
        assertThat(made.tight(20)).isEmpty();
    }
}
