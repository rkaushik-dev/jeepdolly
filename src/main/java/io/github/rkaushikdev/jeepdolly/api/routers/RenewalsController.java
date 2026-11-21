package io.github.rkaushikdev.jeepdolly.api.routers;

import io.github.rkaushikdev.jeepdolly.api.Caller;
import io.github.rkaushikdev.jeepdolly.api.Rejected;
import io.github.rkaushikdev.jeepdolly.api.Requires;
import io.github.rkaushikdev.jeepdolly.api.Role;
import io.github.rkaushikdev.jeepdolly.api.Schemas;
import io.github.rkaushikdev.jeepdolly.api.Schemas.PermitOut;
import io.github.rkaushikdev.jeepdolly.api.Work;
import io.github.rkaushikdev.jeepdolly.services.Renewals;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
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
 * Renewal routes: which annuals are running out, the letters that go to their carriers, and the
 * renewal itself.
 *
 * <p>The window defaults to the office's own renewal rule rather than to a constant, so a site that
 * works six weeks ahead sets it once and both the list and the letters follow. A renewal renews a
 * carrier's annuals in one transaction, or none of them.
 */
@RestController
@RequestMapping("/renewals")
@Tag(name = "renewals")
public class RenewalsController {

    private final Work work;

    public RenewalsController(Work work) {
        this.work = work;
    }

    /** One annual running out. */
    public record DueOut(String number, int usdot, String carrier, LocalDate expires, long daysLeft) {}

    /** One carrier's letter, however many permits are on it. */
    public record LetterOut(
            int usdot, String carrier, int count, LocalDate soonest, List<String> numbers, List<String> lines) {

        public LetterOut {
            numbers = List.copyOf(numbers);
            lines = List.copyOf(lines);
        }
    }

    /** The permits a carrier has asked to renew. */
    public record RenewIn(
            @NotNull(message = Rejected.REQUIRED) @Size(min = 1, message = Schemas.AT_LEAST_ONE)
                    List<String> numbers) {}

    /** What the batch produced. */
    public record RenewedOut(int usdot, List<String> replaced, List<PermitOut> issued) {

        public RenewedOut {
            replaced = List.copyOf(replaced);
            issued = List.copyOf(issued);
        }
    }

    /** Every live annual running out inside the window, soonest first. */
    @GetMapping
    @Requires(Role.READER)
    public List<DueOut> due(
            @RequestParam Optional<LocalDate> on, @RequestParam(required = false) @Min(0) @Max(365) Integer within) {
        LocalDate day = on.orElseGet(LocalDate::now);
        int window = within == null ? work.rules().renewalDays() : within;
        return work.apply(uow -> Renewals.dueBy(uow, day, window).stream()
                .map(one -> new DueOut(one.number(), one.usdot(), one.carrier(), one.expires(), one.daysLeft(day)))
                .toList());
    }

    /** One letter per carrier, which is what goes in the mail merge. */
    @GetMapping("/letters")
    @Requires(Role.READER)
    public List<LetterOut> letters(
            @RequestParam Optional<LocalDate> on, @RequestParam(required = false) @Min(0) @Max(365) Integer within) {
        LocalDate day = on.orElseGet(LocalDate::now);
        int window = within == null ? work.rules().renewalDays() : within;
        return work.apply(uow -> Renewals.letters(uow, day, window).stream()
                .map(one -> new LetterOut(
                        one.usdot(),
                        one.carrier(),
                        one.count(),
                        one.soonest(),
                        one.due().stream().map(Renewals.Due::number).toList(),
                        one.lines()))
                .toList());
    }

    /** Renew a carrier's annuals in one transaction, or none of them. */
    @PostMapping("/{usdot}")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires(Role.CLERK)
    public RenewedOut renew(
            @PathVariable int usdot,
            @Valid @RequestBody RenewIn body,
            @RequestParam Optional<LocalDate> on,
            Caller who) {
        LocalDate day = on.orElseGet(LocalDate::now);
        return work.apply(uow -> {
            Renewals.Renewed found =
                    Renewals.renew(uow, work.office(uow, day), usdot, body.numbers(), day, who.person());
            return new RenewedOut(
                    found.usdot(),
                    found.replaced(),
                    found.issued().stream().map(PermitOut::of).toList());
        });
    }
}
