package io.github.rkaushikdev.jeepdolly;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The three ways the office writes back what it was handed. */
class WrittenTest {

    @Test
    void textIsQuotedTheWayARefusalLetterQuotesIt() {
        assertThat(Written.quoted("about fourteen feet")).isEqualTo("'about fourteen feet'");
        assertThat(Written.quoted("O'Brien")).isEqualTo("\"O'Brien\"");
        assertThat(Written.quoted("both ' and \"")).isEqualTo("'both \\' and \"'");
        assertThat(Written.quoted("tab\there\n")).isEqualTo("'tab\\there\\n'");
        assertThat(Written.quoted("back\\slash\u0001")).isEqualTo("'back\\\\slash\\x01'");
        assertThat(Written.quoted("")).isEqualTo("''");
    }

    @Test
    void onlyPlainDigitsAreANumber() {
        assertThat(Written.isDigits("132400")).isTrue();
        assertThat(Written.isDigits("")).isFalse();
        assertThat(Written.isDigits("-5")).isFalse();
        assertThat(Written.isDigits(" 5")).isFalse();
        assertThat(Written.isDigits("12.4")).isFalse();
    }

    @Test
    void aPlaceNameIsCapitalisedWordByWord() {
        assertThat(Written.title("waterville")).isEqualTo("Waterville");
        assertThat(Written.title("presque isle")).isEqualTo("Presque Isle");
        assertThat(Written.title("o'brien")).isEqualTo("O'Brien");
        assertThat(Written.title("NEW PORTLAND")).isEqualTo("New Portland");
        assertThat(Written.title("route 2a")).isEqualTo("Route 2A");
        assertThat(Written.title("")).isEqualTo("");
    }
}
