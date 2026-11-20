package io.github.rkaushikdev.jeepdolly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.jeepdolly.support.Signatures;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The refusal root, its codes, and the shape a refusal is written down in. */
class ErrorsTest {

    @Test
    void everyRefusalCarriesACodeThatIsNotTheClassName() {
        assertThat(Unreadable.CODE).isEqualTo("unreadable");
        assertThat(Impossible.CODE).isEqualTo("impossible");
        assertThat(NotOnFile.CODE).isEqualTo("not_on_file");
        assertThat(Conflict.CODE).isEqualTo("conflict");
        assertThat(OverLimit.CODE).isEqualTo("over_limit");
        assertThat(Blocked.CODE).isEqualTo("blocked");
        assertThat(NotAllowed.CODE).isEqualTo("not_allowed");
    }

    @Test
    void theCodeTableHoldsAllSevenAndNoRoot() {
        assertThat(Refusal.BY_CODE).hasSize(7);
        assertThat(Refusal.BY_CODE.get("conflict")).isSameAs(Conflict.class);
        assertThat(Refusal.BY_CODE).doesNotContainKey("refused");
    }

    @Test
    void aRefusalPrintsWithItsCodeInFront() {
        OverLimit refusal = new OverLimit("the tandem is 4,000 lb over");
        assertThat(refusal).hasToString("over_limit: the tandem is 4,000 lb over");
        assertThat(refusal.getMessage()).isEqualTo("the tandem is 4,000 lb over");
    }

    @Test
    void aRefusalCarriesTheFactsBehindItForTheLetter() {
        Blocked refusal = new Blocked("route 27 is posted", Facts.of("segment", "27-014", "order", "25-04"));
        assertThat(refusal.facts()).isEqualTo(Map.of("segment", "27-014", "order", "25-04"));
        assertThat(refusal.asMap())
                .isEqualTo(Map.of(
                        "code",
                        "blocked",
                        "message",
                        "route 27 is posted",
                        "facts",
                        Map.of("segment", "27-014", "order", "25-04")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void theFactsOfOneRefusalAreNotSharedWithTheNext() {
        Conflict first = new Conflict("already on file", Facts.of("order", "25-04"));
        Map<String, Object> written = first.asMap();
        ((Map<String, Object>) written.get("facts")).put("order", "25-99");
        assertThat(first.facts().get("order")).isEqualTo("25-04");
    }

    @Test
    void aRefusalWithNothingBehindItStillWritesABody() {
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("code", "not_on_file");
        expected.put("message", "no such carrier");
        expected.put("facts", Map.of());
        assertThat(new NotOnFile("no such carrier").asMap()).isEqualTo(expected);
    }

    @Test
    void everythingCanBeCaughtAsOneRoot() throws ReflectiveOperationException {
        for (Class<? extends Refusal> kind : Refusal.BY_CODE.values()) {
            Refusal made = kind.getConstructor(String.class).newInstance("no");
            assertThatThrownBy(() -> {
                        throw made;
                    })
                    .isInstanceOf(Refusal.class);
        }
    }

    @Test
    void aMeasureThatCannotBeReadRefusesAndSaysWhatWasWritten() {
        Unreadable refusal = catchThrowableOfType(Unreadable.class, () -> Inches.parse("about fourteen feet"));
        assertThat(refusal.facts().get("written")).isEqualTo("about fourteen feet");
        assertThat(refusal.code()).isEqualTo("unreadable");
    }

    @Test
    void handingTheWrongTypeInIsADefectAndNotARefusal() {
        // The original raised a type error at run time for Inches(14.5). Java refuses to compile
        // it, so the constructor's signature is the check, and a defect stays outside the root.
        Signatures.constructorsTakeOnly(Inches.class, int.class);
        assertThat(Refusal.class.isAssignableFrom(ClassCastException.class)).isFalse();
        assertThat(Refusal.class.isAssignableFrom(IllegalArgumentException.class))
                .isFalse();
    }
}
