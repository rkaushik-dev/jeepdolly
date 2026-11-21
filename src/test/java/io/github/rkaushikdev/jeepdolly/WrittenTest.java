package io.github.rkaushikdev.jeepdolly;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The ways the office writes back, and reads, what it was handed. */
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

    @Test
    void aFileIsCutIntoLinesAtEveryBoundaryAnEditorLeaves() {
        assertThat(Written.lines("one\ntwo\r\nthree\rfour")).containsExactly("one", "two", "three", "four");
        assertThat(Written.lines("a\u000bb\u000cc\u001cd\u001de\u001ef\u0085g\u2028h\u2029i"))
                .containsExactly("a", "b", "c", "d", "e", "f", "g", "h", "i");
        assertThat(Written.lines("one\n\ntwo\n")).containsExactly("one", "", "two");
        assertThat(Written.lines("\n")).containsExactly("");
        assertThat(Written.lines("\r\n\r\n")).containsExactly("", "");
        assertThat(Written.lines("no boundary")).containsExactly("no boundary");
        assertThat(Written.lines("")).isEqualTo(List.of());
        assertThat(Written.lines("tab\tstays")).containsExactly("tab\tstays");
    }
}
