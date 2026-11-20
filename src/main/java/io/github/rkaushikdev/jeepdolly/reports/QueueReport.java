package io.github.rkaushikdev.jeepdolly.reports;

import io.github.rkaushikdev.jeepdolly.permits.ApplicationState;
import io.github.rkaushikdev.jeepdolly.persistence.models.ApplicationRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.ReviewRow;
import io.github.rkaushikdev.jeepdolly.structures.ReviewState;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * How old the work is, which is the only number the supervisor watches.
 *
 * <p>Everything else says what happened. This says what has not happened yet, as an age rather than
 * a count, because forty applications filed this morning is a busy day and four filed a fortnight
 * ago is a problem. Applications waiting to be looked at should be hours old. Studies waiting for an
 * engineer are counted in days against a promised fortnight. Studies on hold are waiting for the
 * hauler, and are reported apart so that a fortnight spent waiting for a spacing diagram is not
 * counted against the engineer.
 */
public final class QueueReport {

    private QueueReport() {}

    /** One thing that has not been dealt with, and how long it has waited. */
    public record Waiting(String reference, LocalDate since, int days, String note) {

        /** Something waiting with nothing written beside it. */
        public Waiting(String reference, LocalDate since, int days) {
            this(reference, since, days, "");
        }

        @Override
        public String toString() {
            return reference + "  " + since + "  " + days + " days  " + note;
        }
    }

    /** The three queues on one page. */
    public record Ageing(LocalDate day, List<Waiting> applications, List<Waiting> studies, List<Waiting> held) {

        public Ageing {
            applications = List.copyOf(applications);
            studies = List.copyOf(studies);
            held = List.copyOf(held);
        }

        /** Days the oldest waiting application has waited, or none at all. */
        public int oldestApplication() {
            return applications.stream().mapToInt(Waiting::days).max().orElse(0);
        }

        /** Days the oldest open study has waited, the number quoted on the telephone. */
        public int oldestStudy() {
            return studies.stream().mapToInt(Waiting::days).max().orElse(0);
        }

        public boolean quiet() {
            return applications.isEmpty() && studies.isEmpty() && held.isEmpty();
        }

        /** Everything in the application or study queue older than a promise, oldest first. */
        public List<Waiting> over(int days) {
            return Stream.concat(applications.stream(), studies.stream())
                    .filter(one -> one.days() > days)
                    .sorted(Comparator.comparingInt(Waiting::days).reversed())
                    .toList();
        }

        /** The page as it prints: a heading, a line per item, and a footing. */
        public List<String> lines() {
            List<String> found = new ArrayList<>();
            found.add("waiting on " + day);
            applications.forEach(one -> found.add("application  " + one));
            studies.forEach(one -> found.add("study  " + one));
            held.forEach(one -> found.add("held  " + one));
            found.add(applications.size() + " applications  " + studies.size() + " studies  " + held.size()
                    + " held  oldest study " + oldestStudy() + " days");
            return List.copyOf(found);
        }

        @Override
        public String toString() {
            return applications.size() + " applications, " + studies.size() + " studies";
        }
    }

    private static int daysBetween(LocalDate since, LocalDate day) {
        return (int) ChronoUnit.DAYS.between(since, day);
    }

    /** Read the three queues and age everything in them as at a day. */
    public static Ageing ageing(EntityManager db, LocalDate day) {
        List<Waiting> applications = db
                .createQuery(
                        "select a from ApplicationRow a where a.state = :state order by a.filed, a.reference",
                        ApplicationRow.class)
                .setParameter("state", ApplicationState.SUBMITTED)
                .getResultList()
                .stream()
                .map(row -> new Waiting(
                        row.getReference(), row.getFiled(), daysBetween(row.getFiled(), day), row.getOrigin()))
                .toList();
        List<Waiting> studies = new ArrayList<>();
        List<Waiting> held = new ArrayList<>();
        for (ReviewRow study : db.createQuery(
                        "select r from ReviewRow r order by r.opened, r.reference", ReviewRow.class)
                .getResultList()) {
            Waiting waited = new Waiting(
                    study.getReference(),
                    study.getOpened(),
                    daysBetween(study.getOpened(), day),
                    study.getEngineer().orElse(""));
            if (study.getState() == ReviewState.WAITING || study.getState() == ReviewState.WITH_ENGINEER) {
                studies.add(waited);
            } else if (study.getState() == ReviewState.HELD) {
                held.add(waited);
            }
        }
        return new Ageing(day, applications, studies, held);
    }
}
