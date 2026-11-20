package io.github.rkaushikdev.jeepdolly.permits;

import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.NotAllowed;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * One duplicate copy of a permit, as it was issued.
 *
 * <p>A permit lives on the dash of a truck for a week. It gets wet, it blows out of the window at
 * the weigh station, the driver leaves it in the other tractor. So the office reprints it, and
 * every reprint is a small risk: two pieces of paper with the same number on them, one of which may
 * be in somebody else's cab.
 *
 * <p>Three rules keep that in hand and none of them is a refusal to reprint. Every copy is numbered
 * and the number is on the paper; the original is copy one and is never recorded, because printing
 * the permit when it is issued is not a reprint. Every copy says why. Past a threshold a supervisor
 * signs: three copies of one permit is a carrier with a filing problem rather than a wet piece of
 * paper, and the fourth wants somebody to have looked.
 *
 * <p>A reprint is not an amendment and never touches the permit. Nothing about what was allowed
 * changes, the number does not change, and the permit's own record is untouched. The reprints are a
 * list beside it.
 *
 * @param permit the permit number, as written
 * @param copy the copy number printed on the paper, two or more
 * @param note the words a reprint for another reason has to carry, or empty
 */
public record Reprint(
        String permit, int copy, LocalDate on, Reason reason, String askedBy, String issuedBy, String note) {

    /** Copies past this many want a supervisor rather than a clerk. */
    public static final int FREELY = 3;

    public Reprint {
        if (copy < 2) {
            throw new Impossible("the original is copy one and is not a reprint");
        }
        if (askedBy.isBlank()) {
            throw new Impossible("a reprint names who asked for it");
        }
        if (reason.wantsWords() && note.isBlank()) {
            throw new Impossible("a reprint for another reason says what the reason was");
        }
    }

    /** A copy asked for on one of the four ordinary grounds, with no note. */
    public Reprint(String permit, int copy, LocalDate on, Reason reason, String askedBy, String issuedBy) {
        this(permit, copy, on, reason, askedBy, issuedBy, "");
    }

    /** Whether this copy wanted a signature at the office's usual limit. */
    public boolean neededASupervisor() {
        return neededASupervisor(FREELY);
    }

    /**
     * Whether this copy wanted a signature, at the limit in force.
     *
     * <p>Takes the limit rather than reading the constant, because the answer is a fact about the day
     * it was printed and a site that changed the limit last month must not change what an old record
     * says.
     */
    public boolean neededASupervisor(int freely) {
        return copy > freely;
    }

    /** What goes across the face of the paper: {@code DUPLICATE - COPY 3}. */
    public String banner() {
        return "DUPLICATE - COPY " + copy;
    }

    /** As the register reads: {@code 25-P-004471 copy 2  2025-04-09  lost}. */
    @Override
    public String toString() {
        String words = note.isEmpty() ? "" : ", " + note;
        return permit + " copy " + copy + "  " + on + "  " + reason + words;
    }

    /**
     * The number of the copy about to be printed.
     *
     * <p>Taken from the highest rather than from the count. A reprint record that was deleted in a
     * tidy up must not cause the office to print two copy threes, and the highest number ever issued
     * is the only thing that cannot go backwards.
     */
    public static int nextCopy(Collection<Integer> taken) {
        return taken.stream().mapToInt(Integer::intValue).max().orElse(1) + 1;
    }

    /** Whether this copy may be printed at the usual limit, refusing if it may not. */
    public static void allowed(Permit permit, LocalDate on, int copy, boolean supervisor) {
        allowed(permit, on, copy, supervisor, FREELY);
    }

    /**
     * Whether this copy may be printed at all, refusing if it may not.
     *
     * <p>A void permit is refused because there is nothing to be on the road with. An expired one is
     * refused for the same reason and it surprises people: a hauler who wants a copy of last month's
     * permit for his own file is asking for a document that looks exactly like a live permit, and the
     * office will not print one. He gets the register entry instead.
     *
     * @throws NotAllowed when the permit is not live, has expired, or the copy wants a supervisor
     *     who is not there
     */
    public static void allowed(Permit permit, LocalDate on, int copy, boolean supervisor, int freely) {
        if (permit.state() != PermitState.ISSUED) {
            throw new NotAllowed(permit.number() + " is " + permit.state() + " and cannot be copied");
        }
        if (permit.expiredOn(on)) {
            throw new NotAllowed(permit.number() + " expired on " + permit.end() + " and cannot be copied");
        }
        if (copy > freely && !supervisor) {
            throw new NotAllowed(
                    "copy " + copy + " of " + permit.number() + " wants a supervisor",
                    Facts.of("permit", permit.number().toString(), "copy", copy));
        }
    }

    /** The first reprint of a permit, asked for by a clerk. */
    public static Reprint issue(Permit permit, LocalDate on, Reason reason, String askedBy, String issuedBy) {
        return issue(permit, on, reason, askedBy, issuedBy, List.of(), false, "", FREELY);
    }

    /** Another reprint of a permit that already has copies, asked for by a clerk. */
    public static Reprint issue(
            Permit permit, LocalDate on, Reason reason, String askedBy, String issuedBy, Collection<Integer> taken) {
        return issue(permit, on, reason, askedBy, issuedBy, taken, false, "", FREELY);
    }

    /** Another reprint, saying whether a supervisor is signing for it. */
    public static Reprint issue(
            Permit permit,
            LocalDate on,
            Reason reason,
            String askedBy,
            String issuedBy,
            Collection<Integer> taken,
            boolean supervisor) {
        return issue(permit, on, reason, askedBy, issuedBy, taken, supervisor, "", FREELY);
    }

    /**
     * Work out the copy number, check it may be printed, and record it.
     *
     * @param taken the copy numbers already printed
     * @param freely the copies a clerk may print without a supervisor
     * @throws NotAllowed when the copy may not be printed
     */
    public static Reprint issue(
            Permit permit,
            LocalDate on,
            Reason reason,
            String askedBy,
            String issuedBy,
            Collection<Integer> taken,
            boolean supervisor,
            String note,
            int freely) {
        int copy = nextCopy(taken);
        allowed(permit, on, copy, supervisor, freely);
        return new Reprint(permit.number().toString(), copy, on, reason, askedBy, issuedBy, note);
    }
}
