package io.github.rkaushikdev.jeepdolly.api.routers;

import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.NotAllowed;
import io.github.rkaushikdev.jeepdolly.Pounds;
import io.github.rkaushikdev.jeepdolly.api.Caller;
import io.github.rkaushikdev.jeepdolly.api.Deps;
import io.github.rkaushikdev.jeepdolly.api.Rejected;
import io.github.rkaushikdev.jeepdolly.api.Requires;
import io.github.rkaushikdev.jeepdolly.api.Role;
import io.github.rkaushikdev.jeepdolly.api.Schemas.EnvelopeIn;
import io.github.rkaushikdev.jeepdolly.api.Schemas.PermitOut;
import io.github.rkaushikdev.jeepdolly.api.Work;
import io.github.rkaushikdev.jeepdolly.domain.Carrier;
import io.github.rkaushikdev.jeepdolly.domain.Commodity;
import io.github.rkaushikdev.jeepdolly.permits.Annual;
import io.github.rkaushikdev.jeepdolly.permits.Application;
import io.github.rkaushikdev.jeepdolly.permits.Eligibility;
import io.github.rkaushikdev.jeepdolly.permits.Office;
import io.github.rkaushikdev.jeepdolly.permits.Permit;
import io.github.rkaushikdev.jeepdolly.services.Audit;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Annual permit routes: the question a carrier asks before paying for a year, and the permit
 * itself.
 *
 * <p>An annual is written against an application already on file, for a combination inside the
 * annual envelope, to a carrier in good standing for the day it starts. The question writes
 * nothing down.
 */
@RestController
@RequestMapping("/annual")
@Tag(name = "annual")
public class AnnualController {

    private final Work work;

    public AnnualController(Work work) {
        this.work = work;
    }

    /** A combination somebody is thinking about. */
    public record EligibilityIn(
            @NotNull(message = Rejected.REQUIRED) @Valid EnvelopeIn envelope,
            @NotNull(message = Rejected.REQUIRED) String gross,
            @NotNull(message = Rejected.REQUIRED) String commodity) {}

    /** Whether it can hold an annual, and the envelope it was read against. */
    public record EligibilityOut(
            boolean allowed,
            List<String> reasons,
            String envelopeWidth,
            String envelopeHeight,
            String envelopeLength,
            String grossCap) {

        public EligibilityOut {
            reasons = List.copyOf(reasons);
        }
    }

    /** Read the annual envelope against a combination, and write nothing. */
    @PostMapping("/eligibility")
    @Requires(Role.READER)
    public EligibilityOut checkEligibility(@Valid @RequestBody EligibilityIn body) {
        return work.apply(uow -> {
            Commodity commodity = uow.commodities().book().get(body.commodity());
            Eligibility found = Annual.eligible(body.envelope().toDomain(), Pounds.parse(body.gross()), commodity);
            return new EligibilityOut(
                    found.allowed(),
                    found.reasons(),
                    Annual.ENVELOPE.width().toString(),
                    Annual.ENVELOPE.height().toString(),
                    Annual.ENVELOPE.length().toString(),
                    Annual.GROSS.toString());
        });
    }

    /**
     * Write an annual against an application already on file.
     *
     * @throws NotAllowed with the carrier's usdot when it is not in good standing, or with the
     *     reasons when the combination cannot hold an annual
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Requires(Role.CLERK)
    public PermitOut issue(@RequestParam String reference, @RequestParam Optional<LocalDate> on, Caller who) {
        LocalDate day = on.orElseGet(LocalDate::now);
        return work.apply(uow -> {
            Application application = uow.applications().get(reference);
            Office desk = Deps.officeFrom(uow, day);
            Carrier carrier = desk.carriers().get(application.usdot());
            Optional<String> standing =
                    carrier.standing(day, day, application.gross().value());
            if (standing.isPresent()) {
                throw new NotAllowed(standing.get(), Facts.of("usdot", application.usdot()));
            }
            Permit permit = Annual.issue(
                    desk.numbering(),
                    desk.library(),
                    desk.schedules().on(day),
                    application.reference(),
                    application.usdot(),
                    carrier.name(),
                    application.combination().envelope(),
                    application.gross(),
                    desk.commodities().get(application.commodity()),
                    day);
            application.issue(day, permit.number().toString());
            uow.applications().saveState(application);
            uow.permits().add(permit);
            Audit.record(
                    uow.entityManager(),
                    "permit.issued",
                    who.person(),
                    "permit",
                    permit.number().toString(),
                    "annual to " + carrier.name());
            return PermitOut.of(permit);
        });
    }
}
