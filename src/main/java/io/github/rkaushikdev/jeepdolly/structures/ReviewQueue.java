package io.github.rkaushikdev.jeepdolly.structures;

import io.github.rkaushikdev.jeepdolly.Conflict;
import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Every engineering study, open and closed.
 *
 * <p>The queue is small, four or five a week in winter and thirty in July. It is worked oldest
 * first, and the supervisor reads the overdue list on Monday.
 */
public final class ReviewQueue {

    private static final Comparator<Review> OLDEST_FIRST =
            Comparator.comparing(Review::opened).thenComparing(Review::reference);

    private final Map<String, Review> byReference = new LinkedHashMap<>();

    /**
     * Queue a study of an application against the structures it is about.
     *
     * @throws Conflict when the application is already in the queue
     * @throws io.github.rkaushikdev.jeepdolly.Impossible when there is no application to open it
     *     against
     */
    public Review open(String reference, List<String> structures, LocalDate on) {
        if (byReference.containsKey(reference)) {
            throw new Conflict(reference + " is already in the queue", Facts.of("reference", reference));
        }
        Review review = new Review(reference, structures, on);
        byReference.put(reference, review);
        return review;
    }

    /** @throws NotOnFile when no review was opened for that application */
    public Review get(String reference) {
        Review found = byReference.get(reference);
        if (found == null) {
            throw new NotOnFile("no review for " + reference, Facts.of("reference", reference));
        }
        return found;
    }

    /** The items nobody has picked up, oldest first, because that is the order they are worked. */
    public List<Review> waiting() {
        return byReference.values().stream()
                .filter(one -> one.state() == ReviewState.WAITING)
                .sorted(OLDEST_FIRST)
                .toList();
    }

    /** What is on one engineer's desk, by reference. */
    public List<Review> withEngineer(String engineer) {
        return byReference.values().stream()
                .filter(one -> Objects.equals(one.engineer().orElse(null), engineer)
                        && one.state() == ReviewState.WITH_ENGINEER)
                .sorted(Comparator.comparing(Review::reference))
                .toList();
    }

    /** Everything still in the queue, oldest first. */
    public List<Review> openItems() {
        return byReference.values().stream()
                .filter(one -> one.state().open())
                .sorted(OLDEST_FIRST)
                .toList();
    }

    /** Open items that have waited more than {@code days}, oldest first. */
    public List<Review> overdue(LocalDate on, int days) {
        return openItems().stream().filter(one -> one.waitingDays(on) > days).toList();
    }

    /** Every item about one structure, by reference. */
    public List<Review> touching(String structure) {
        return byReference.values().stream()
                .filter(one -> one.structures().contains(structure))
                .sorted(Comparator.comparing(Review::reference))
                .toList();
    }

    public int size() {
        return byReference.size();
    }
}
