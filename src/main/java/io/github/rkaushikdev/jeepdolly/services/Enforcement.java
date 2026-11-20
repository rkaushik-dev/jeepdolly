package io.github.rkaushikdev.jeepdolly.services;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.domain.Carrier;
import io.github.rkaushikdev.jeepdolly.enforcement.Citation;
import io.github.rkaushikdev.jeepdolly.enforcement.Offence;
import io.github.rkaushikdev.jeepdolly.enforcement.Penalty;
import io.github.rkaushikdev.jeepdolly.enforcement.PenaltySchedule;
import io.github.rkaushikdev.jeepdolly.permits.Series;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Filing a roadside citation, which is four steps a clerk used to do by hand.
 *
 * <p>A ticket arrives in the post. The clerk finds the carrier, counts what else that carrier has
 * had in the last year, works out the money off the schedule that was in force on the day the ticket
 * was written, and files it. Every one of those has been got wrong at least once, and the count is
 * the one got wrong most, because it wants a decision about dismissed tickets that used to live in
 * one supervisor's head. So it is here, once, and the day is a parameter.
 *
 * <p>Two things this deliberately does not do. It does not take money: a citation is paid at the
 * till like everything else, and this only moves it to paid when told the money arrived. It does
 * not suspend a carrier: enough citations in a year is grounds for a suspension, and the
 * supervisor makes that decision on the paper, because a system that suspended automatically would
 * suspend over a ticket that was going to be dismissed the following week.
 */
public final class Enforcement {

    /** The series citations are numbered in, off the same counter as everything else. */
    public static final Series CITATION_SERIES = Series.CITATION;

    private Enforcement() {}

    /**
     * The penalty schedule in force on a day.
     *
     * <p>There is one schedule and it is in the code. When the legislature amends the penalties this
     * becomes a lookup on a table and the callers do not change, which is why they all ask for it by
     * day rather than reaching for the standard one themselves.
     */
    public static PenaltySchedule scheduleOn(LocalDate day) {
        return PenaltySchedule.standard();
    }

    /** File a ticket that read no scale and names no permit. */
    public static Citation fileCitation(
            UnitOfWork work,
            String number,
            int usdot,
            String officer,
            Offence offence,
            String place,
            LocalDate written,
            LocalDate filed,
            String who) {
        return fileCitation(
                work,
                number,
                usdot,
                officer,
                offence,
                place,
                written,
                filed,
                who,
                Optional.empty(),
                Optional.empty(),
                "");
    }

    /**
     * Price a roadside ticket against the carrier's year and put it on file.
     *
     * @param over what the scale read over the limit; kept only for an offence priced by weight
     * @param permit the permit the load was travelling under, when there was one
     * @throws Impossible when the ticket is filed before it was written
     * @throws io.github.rkaushikdev.jeepdolly.NotOnFile when nobody registered the carrier
     */
    public static Citation fileCitation(
            UnitOfWork work,
            String number,
            int usdot,
            String officer,
            Offence offence,
            String place,
            LocalDate written,
            LocalDate filed,
            String who,
            Optional<Pounds> over,
            Optional<String> permit,
            String note) {
        if (filed.isBefore(written)) {
            throw new Impossible(number + " cannot be filed before it was written");
        }
        Carrier carrier = work.carriers().get(usdot, written);
        PenaltySchedule schedule = scheduleOn(written);
        int priors = work.citations().priors(usdot, written);
        Citation.Builder citation = Citation.builder()
                .number(number)
                .written(written)
                .filed(filed)
                .officer(officer)
                .usdot(usdot)
                .carrierName(carrier.name())
                .offence(offence)
                .place(place)
                .assessed(Penalty.assess(schedule, offence, over, priors))
                .note(note);
        permit.ifPresent(citation::permit);
        if (offence.byWeight()) {
            over.ifPresent(citation::over);
        }
        Citation made = citation.build();
        work.citations().add(made, schedule.effective());
        Audit.record(work.entityManager(), "citation.filed", who, "citation", number, offence + " " + made.amount());
        return made;
    }

    /** The carrier says no, in writing, inside the window. */
    public static Citation contest(UnitOfWork work, String number, LocalDate on, String reason, String who) {
        Citation citation = work.citations().get(number);
        citation.contest(on, reason);
        work.citations().saveState(citation);
        Audit.record(work.entityManager(), "citation.contested", who, "citation", number, reason);
        return citation;
    }

    /** The hearing went the office's way. */
    public static Citation uphold(UnitOfWork work, String number, LocalDate on, String who) {
        return uphold(work, number, on, who, "");
    }

    /** The hearing went the office's way, with a note on why. */
    public static Citation uphold(UnitOfWork work, String number, LocalDate on, String who, String note) {
        Citation citation = work.citations().get(number);
        citation.uphold(on, note);
        work.citations().saveState(citation);
        Audit.record(work.entityManager(), "citation.upheld", who, "citation", number, note);
        return citation;
    }

    /** The hearing went the carrier's way, and it stops being a prior offence. */
    public static Citation dismiss(UnitOfWork work, String number, LocalDate on, String reason, String who) {
        Citation citation = work.citations().get(number);
        citation.dismiss(on, reason);
        work.citations().saveState(citation, reason);
        Audit.record(work.entityManager(), "citation.dismissed", who, "citation", number, reason);
        return citation;
    }

    /** Money arrived at the till and this is the paper catching up. */
    public static Citation pay(UnitOfWork work, String number, LocalDate on, String who) {
        return pay(work, number, on, who, "");
    }

    /** Money arrived at the till under a reference, and this is the paper catching up. */
    public static Citation pay(UnitOfWork work, String number, LocalDate on, String who, String reference) {
        Citation citation = work.citations().get(number);
        citation.pay(on, reference);
        work.citations().saveState(citation);
        Audit.record(work.entityManager(), "citation.paid", who, "citation", number, reference);
        return citation;
    }

    /** The office's own mistake, killed by a supervisor with a reason on it. */
    public static Citation makeVoid(UnitOfWork work, String number, LocalDate on, String reason, String who) {
        Citation citation = work.citations().get(number);
        citation.makeVoid(on, reason);
        work.citations().saveState(citation, reason);
        Audit.record(work.entityManager(), "citation.voided", who, "citation", number, reason);
        return citation;
    }

    /** A citation number off the same counter every other document uses. */
    public static String nextNumber(UnitOfWork work, LocalDate on) {
        return work.counter().number(on, CITATION_SERIES).toString();
    }

    /** How many cents a carrier still owes on citations, for the counter screen. */
    public static long owedBy(UnitOfWork work, int usdot) {
        return work.citations().forCarrier(usdot).stream()
                .filter(one -> one.getState().owing())
                .mapToLong(one -> one.getAmount().cents())
                .sum();
    }
}
