package io.github.rkaushikdev.jeepdolly.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.persistence.models.AuditRow;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The trail, its fixed vocabulary, and the questions asked of it. */
class AuditTest {

    private static final LocalDateTime NINE = LocalDateTime.of(2025, 4, 7, 9, 0);
    private static final LocalDateTime TEN = LocalDateTime.of(2025, 4, 7, 10, 0);
    private static final LocalDateTime LATER = LocalDateTime.of(2025, 4, 8, 9, 0);

    /** What an action may be about. A new word here is a deliberate decision. */
    private static final Set<String> SUBJECTS = Set.of(
            "application",
            "carrier",
            "citation",
            "key",
            "payment",
            "permit",
            "pilot",
            "review",
            "road",
            "structure",
            "tariff");

    private UnitOfWork session;
    private EntityManager db;

    @BeforeEach
    void open() {
        session = Databases.empty().unitOfWork();
        db = session.entityManager();
    }

    @AfterEach
    void close() {
        session.close();
    }

    private List<AuditRow> everything() {
        return db.createQuery("select a from AuditRow a", AuditRow.class).getResultList();
    }

    @Test
    void aLineSaysWhoDidWhatToWhichDocument() {
        Audit.record(db, "permit.issued", "D Bickford", "permit", "25-P-004471", NINE);
        session.commit();
        assertThat(everything()).hasSize(1);
        AuditRow row = everything().get(0);
        assertThat(row.getAction()).isEqualTo("permit.issued");
        assertThat(row.getActor()).isEqualTo("D Bickford");
        assertThat(row.getKind()).isEqualTo("permit");
        assertThat(row.getReference()).isEqualTo("25-P-004471");
        assertThat(row.getHappened()).isEqualTo(NINE);
        assertThat(row).hasToString("<AuditRow permit.issued permit:25-P-004471>");
    }

    @Test
    void theDetailIsTheOnlyPlaceAnybodyWritesASentence() {
        Audit.record(
                db, "permit.voided", "R Pelletier", "permit", "25-P-004471", NINE, "load cancelled by the customer");
        session.commit();
        assertThat(everything()).singleElement().satisfies(one -> assertThat(one.getDetail())
                .startsWith("load cancelled"));
    }

    @Test
    void anActionTheTrailDoesNotKnowIsRefused() {
        Impossible refusal = catchThrowableOfType(
                Impossible.class, () -> Audit.record(db, "issued a permit", "D Bickford", "permit", "25-P-004471"));
        assertThat(refusal.facts().get("action")).isEqualTo("issued a permit");
    }

    @Test
    void aLineWithNobodyBehindItIsRefused() {
        assertThatThrownBy(() -> Audit.record(db, "permit.issued", "   ", "permit", "25-P-004471"))
                .isInstanceOf(Impossible.class);
    }

    @Test
    void theTrailOfOneDocumentIsOldestFirst() {
        Audit.record(db, "application.filed", "clerk", "application", "25-F-1", NINE);
        Audit.record(db, "application.submitted", "clerk", "application", "25-F-1", TEN);
        Audit.record(db, "permit.issued", "clerk", "permit", "25-P-1", LATER);
        session.commit();
        assertThat(Audit.forDocument(db, "application", "25-F-1"))
                .extracting(AuditRow::getAction)
                .containsExactly("application.filed", "application.submitted");
    }

    @Test
    void theTrailOfOnePersonIsNewestFirst() {
        Audit.record(db, "application.filed", "clerk", "application", "25-F-1", NINE);
        Audit.record(db, "permit.issued", "clerk", "permit", "25-P-1", LATER);
        Audit.record(db, "permit.issued", "somebody else", "permit", "25-P-2", TEN);
        session.commit();
        assertThat(Audit.byPerson(db, "clerk"))
                .extracting(AuditRow::getReference)
                .containsExactly("25-P-1", "25-F-1");
    }

    @Test
    void oneActionCanBeCountedForTheMonth() {
        for (int number = 0; number < 3; number++) {
            Audit.record(db, "permit.issued", "clerk", "permit", "25-P-" + number, NINE);
        }
        Audit.record(db, "permit.voided", "clerk", "permit", "25-P-0", TEN);
        session.commit();
        assertThat(Audit.ofAction(db, "permit.issued")).hasSize(3);
        assertThat(Audit.ofAction(db, "permit.voided")).hasSize(1);
    }

    @Test
    void aSpanOfTheTrailIsWhatAnAuditorAsksFor() {
        Audit.record(db, "permit.issued", "clerk", "permit", "25-P-1", NINE);
        Audit.record(db, "permit.issued", "clerk", "permit", "25-P-2", LATER);
        session.commit();
        assertThat(Audit.between(db, NINE, TEN))
                .extracting(AuditRow::getReference)
                .containsExactly("25-P-1");
    }

    @Test
    void aSpanThatEndsBeforeItStartsIsRefused() {
        assertThatThrownBy(() -> Audit.between(db, LATER, NINE)).isInstanceOf(Impossible.class);
    }

    @Test
    void theTrailGoesBackWithTheWorkThatWasRolledBack() {
        Audit.record(db, "permit.issued", "clerk", "permit", "25-P-1", NINE);
        session.rollback();
        assertThat(everything()).isEmpty();
    }

    @Test
    void theVocabularyCoversTheThingsTheOfficeDoes() {
        assertThat(Audit.ACTIONS)
                .contains("permit.issued", "carrier.suspended", "road.closed", "tariff.filed", "citation.filed");
    }

    @Test
    void everyActionIsASubjectAndAPastTenseVerb() {
        // The shape is what matters, not a count: a subject this office has, and one word for what
        // happened to it.
        for (String action : Audit.ACTIONS) {
            int dot = action.indexOf('.');
            String subject = dot < 0 ? action : action.substring(0, dot);
            String verb = dot < 0 ? "" : action.substring(dot + 1);
            assertThat(SUBJECTS).as(action).contains(subject);
            assertThat(verb).as(action).isNotEmpty().isLowerCase();
        }
    }

    @Test
    void everySubjectTheOfficeHasAppearsAtLeastOnce() {
        Set<String> found = Audit.ACTIONS.stream()
                .map(one -> one.substring(0, one.indexOf('.')))
                .collect(Collectors.toSet());
        assertThat(found).isEqualTo(SUBJECTS);
    }
}
