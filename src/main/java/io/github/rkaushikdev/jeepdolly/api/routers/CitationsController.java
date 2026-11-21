package io.github.rkaushikdev.jeepdolly.api.routers;

import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.Unreadable;
import io.github.rkaushikdev.jeepdolly.Written;
import io.github.rkaushikdev.jeepdolly.api.Caller;
import io.github.rkaushikdev.jeepdolly.api.Rejected;
import io.github.rkaushikdev.jeepdolly.api.Requires;
import io.github.rkaushikdev.jeepdolly.api.Role;
import io.github.rkaushikdev.jeepdolly.api.Work;
import io.github.rkaushikdev.jeepdolly.enforcement.Citation;
import io.github.rkaushikdev.jeepdolly.enforcement.Offence;
import io.github.rkaushikdev.jeepdolly.persistence.models.CitationRow;
import io.github.rkaushikdev.jeepdolly.services.Enforcement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Citation routes, which are mostly the counter typing in what the post brought.
 *
 * <p>The filing route takes a number rather than allocating one, and that is not an oversight. The
 * number is on the ticket the officer wrote, printed on a pad in a patrol car, and the office's job
 * is to record it rather than to invent a second one. The counter series exists for the handful the
 * office writes itself, and the route for those asks for a number first.
 *
 * <p>Every route that moves a citation takes the day it happened on, because the post is three days
 * behind and the day a hearing was held is not the day somebody got round to typing it in.
 *
 * <p>Paying is a clerk's job and everything else that closes a citation is a supervisor's. That
 * split is the office's and not a technical one: a clerk takes money over the counter all day, and a
 * clerk who could dismiss a ticket would be a clerk somebody leaned on.
 */
@RestController
@RequestMapping("/citations")
@Tag(name = "citations")
public class CitationsController {

    private final Work work;

    public CitationsController(Work work) {
        this.work = work;
    }

    /**
     * A ticket as it arrives in the post.
     *
     * @param filed the day it reached the office, or the day it was written when left out
     * @param over what the scale read over the limit, for an offence priced by weight
     * @param permit the permit the load was travelling under, when there was one
     */
    public record CitationIn(
            @NotNull(message = Rejected.REQUIRED) String number,
            @NotNull(message = Rejected.REQUIRED) Integer usdot,
            @NotNull(message = Rejected.REQUIRED) String officer,
            @NotNull(message = Rejected.REQUIRED) String offence,
            @NotNull(message = Rejected.REQUIRED) String place,
            @NotNull(message = Rejected.REQUIRED) LocalDate written,
            Optional<LocalDate> filed,
            Optional<String> over,
            Optional<String> permit,
            @Size(max = 500) String note) {

        public CitationIn {
            filed = filed == null ? Optional.empty() : filed;
            over = over == null ? Optional.empty() : over;
            permit = permit == null ? Optional.empty() : permit;
            note = note == null ? "" : note;
        }

        /**
         * The offence, as the schedule prices it.
         *
         * @throws Unreadable when it is not an offence this office prices
         */
        public Offence kind() {
            try {
                return Offence.of(offence);
            } catch (IllegalArgumentException unknown) {
                throw new Unreadable(
                        Written.quoted(offence) + " is not an offence this office prices",
                        Facts.of("offence", offence));
            }
        }

        /** What the scale read over the limit, when it was weighed. */
        public Optional<Pounds> overage() {
            return over.map(Pounds::parse);
        }
    }

    /**
     * A citation as the office holds it.
     *
     * @param permit the permit the load was travelling under, when there was one
     * @param over what the scale read over the limit, for an offence priced by weight
     */
    public record CitationOut(
            String number,
            LocalDate written,
            LocalDate filed,
            int usdot,
            String carrierName,
            String officer,
            String offence,
            String place,
            Optional<String> permit,
            Optional<String> over,
            String base,
            int multiplier,
            String amount,
            String state,
            LocalDate contestCloses) {

        public static CitationOut of(Citation citation) {
            return new CitationOut(
                    citation.number(),
                    citation.written(),
                    citation.filed(),
                    citation.usdot(),
                    citation.carrierName(),
                    citation.officer(),
                    citation.offence().value(),
                    citation.place(),
                    citation.permit(),
                    citation.over().map(Pounds::toString),
                    citation.assessed().base().toString(),
                    citation.assessed().multiplier(),
                    citation.amount().toString(),
                    citation.state().value(),
                    citation.contestCloses());
        }

        static CitationOut of(CitationRow row) {
            return of(row.toDomain());
        }
    }

    /** Put a roadside ticket on file, priced against the carrier's year. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Requires(Role.CLERK)
    public CitationOut fileCitation(@Valid @RequestBody CitationIn body, Caller who) {
        return work.apply(uow -> CitationOut.of(Enforcement.fileCitation(
                uow,
                body.number(),
                body.usdot(),
                body.officer(),
                body.kind(),
                body.place(),
                body.written(),
                body.filed().orElse(body.written()),
                who.person(),
                body.overage(),
                body.permit(),
                body.note())));
    }

    /** A number for the few the office writes itself. */
    @GetMapping("/next-number")
    @Requires(Role.CLERK)
    public Map<String, String> nextNumber(@RequestParam Optional<LocalDate> on) {
        LocalDate day = on.orElseGet(LocalDate::now);
        return work.apply(uow -> Map.of("number", Enforcement.nextNumber(uow, day)));
    }

    /** The chase list: everything still expecting money, oldest first. */
    @GetMapping("/owing")
    @Requires(Role.READER)
    public List<CitationOut> owing() {
        return work.apply(
                uow -> uow.citations().owing().stream().map(CitationOut::of).toList());
    }

    /** Everything against one carrier, newest first. */
    @GetMapping("/carrier/{usdot}")
    @Requires(Role.READER)
    public List<CitationOut> forCarrier(@PathVariable int usdot) {
        return work.apply(uow ->
                uow.citations().forCarrier(usdot).stream().map(CitationOut::of).toList());
    }

    /** One citation, as it stands. */
    @GetMapping("/{number}")
    @Requires(Role.READER)
    public CitationOut readCitation(@PathVariable String number) {
        return work.apply(uow -> CitationOut.of(uow.citations().get(number)));
    }

    /** The carrier said no, in writing, inside the window. */
    @PostMapping("/{number}/contest")
    @Requires(Role.CLERK)
    public CitationOut contest(
            @PathVariable String number,
            @RequestParam @Size(min = 3, max = 500) String reason,
            @RequestParam Optional<LocalDate> on,
            Caller who) {
        LocalDate day = on.orElseGet(LocalDate::now);
        return work.apply(uow -> CitationOut.of(Enforcement.contest(uow, number, day, reason, who.person())));
    }

    /** The hearing went the office's way and it still wants paying. */
    @PostMapping("/{number}/uphold")
    @Requires(Role.SUPERVISOR)
    public CitationOut uphold(
            @PathVariable String number,
            @RequestParam(defaultValue = "") @Size(max = 500) String note,
            @RequestParam Optional<LocalDate> on,
            Caller who) {
        LocalDate day = on.orElseGet(LocalDate::now);
        return work.apply(uow -> CitationOut.of(Enforcement.uphold(uow, number, day, who.person(), note)));
    }

    /** The hearing went the carrier's way and it stops being a prior offence. */
    @PostMapping("/{number}/dismiss")
    @Requires(Role.SUPERVISOR)
    public CitationOut dismiss(
            @PathVariable String number,
            @RequestParam @Size(min = 3, max = 500) String reason,
            @RequestParam Optional<LocalDate> on,
            Caller who) {
        LocalDate day = on.orElseGet(LocalDate::now);
        return work.apply(uow -> CitationOut.of(Enforcement.dismiss(uow, number, day, reason, who.person())));
    }

    /** Money arrived at the till and this is the paper catching up. */
    @PostMapping("/{number}/payment")
    @Requires(Role.CLERK)
    public CitationOut pay(
            @PathVariable String number,
            @RequestParam(defaultValue = "") @Size(max = 64) String reference,
            @RequestParam Optional<LocalDate> on,
            Caller who) {
        LocalDate day = on.orElseGet(LocalDate::now);
        return work.apply(uow -> CitationOut.of(Enforcement.pay(uow, number, day, who.person(), reference)));
    }

    /** The office's own mistake, killed with a reason on it. */
    @PostMapping("/{number}/void")
    @Requires(Role.SUPERVISOR)
    public CitationOut voidCitation(
            @PathVariable String number,
            @RequestParam @Size(min = 3, max = 500) String reason,
            @RequestParam Optional<LocalDate> on,
            Caller who) {
        LocalDate day = on.orElseGet(LocalDate::now);
        return work.apply(uow -> CitationOut.of(Enforcement.makeVoid(uow, number, day, reason, who.person())));
    }
}
