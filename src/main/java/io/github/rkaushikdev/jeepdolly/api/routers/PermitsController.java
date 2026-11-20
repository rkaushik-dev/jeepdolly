package io.github.rkaushikdev.jeepdolly.api.routers;

import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.Logs;
import io.github.rkaushikdev.jeepdolly.NotAllowed;
import io.github.rkaushikdev.jeepdolly.api.Caller;
import io.github.rkaushikdev.jeepdolly.api.Deps;
import io.github.rkaushikdev.jeepdolly.api.Requires;
import io.github.rkaushikdev.jeepdolly.api.Role;
import io.github.rkaushikdev.jeepdolly.api.Schemas.ApplicationIn;
import io.github.rkaushikdev.jeepdolly.api.Schemas.PermitOut;
import io.github.rkaushikdev.jeepdolly.api.Work;
import io.github.rkaushikdev.jeepdolly.permits.Application;
import io.github.rkaushikdev.jeepdolly.permits.Office;
import io.github.rkaushikdev.jeepdolly.permits.Permit;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.persistence.models.PermitRow;
import io.github.rkaushikdev.jeepdolly.services.Counter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
 * Permit routes: issue one, read one, void one, see what is on the road.
 *
 * <p>Issuing is the only route in the service that takes money and hands out a number, so it is
 * the one to read carefully. It assesses the application again rather than trusting an assessment
 * the client sends back: a client that could post an assessment could post a cheaper one. The
 * number is taken from the counter inside this request's transaction, so a failure anywhere after
 * it rolls the number back with the permit.
 *
 * <p>The walk in route is the one the counter screen actually uses. A hauler at the window with a
 * load on the truck gets a permit or a reason in one request, and the office does not want a half
 * filed application left behind either way.
 */
@RestController
@RequestMapping("/permits")
@Tag(name = "permits")
public class PermitsController {

    private final Work work;

    public PermitsController(Work work) {
        this.work = work;
    }

    /** Assess an application again and issue against it. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Requires(Role.CLERK)
    public PermitOut issuePermit(@RequestParam String reference, @RequestParam Optional<LocalDate> on, Caller who) {
        LocalDate day = on.orElseGet(LocalDate::now);
        Permit permit = work.apply(uow -> {
            Application application = uow.applications().get(reference);
            Office desk = Deps.officeFrom(uow, application.start());
            return Counter.writePermit(uow, desk, reference, who.person(), day).permit();
        });
        return issued(permit, who);
    }

    /**
     * File, submit and issue in one request, which is what a window is. A refusal is part of the
     * record, so the refused application is kept before the refusal goes back.
     */
    @PostMapping("/walk-in")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires(Role.CLERK)
    public PermitOut walkIn(@Valid @RequestBody ApplicationIn body, @RequestParam Optional<LocalDate> on, Caller who) {
        LocalDate day = on.orElseGet(LocalDate::now);
        Permit permit = work.apply(uow -> {
            String reference = Counter.nextReference(uow, day);
            Application application =
                    body.toDomain(reference, day, uow.commodities().book(day));
            Office desk = Deps.officeFrom(uow, application.start());
            try {
                return Counter.overTheCounter(uow, desk, application, who.person(), day)
                        .permit();
            } catch (NotAllowed refused) {
                uow.commit();
                throw refused;
            }
        });
        return issued(permit, who);
    }

    /** One permit, complete, as it was printed. */
    @GetMapping("/{number}")
    @Requires(Role.READER)
    public PermitOut readPermit(@PathVariable String number) {
        return work.apply(uow -> PermitOut.of(uow.permits().get(number)));
    }

    /** Everything valid for travel on a day. */
    @GetMapping
    @Requires(Role.READER)
    public List<PermitOut> permitsOnTheRoad(@RequestParam Optional<LocalDate> day) {
        LocalDate when = day.orElseGet(LocalDate::now);
        return work.apply(uow -> shape(uow, uow.permits().onTheRoad(when)));
    }

    /** What the office rings about on a Friday afternoon. */
    @GetMapping("/expiring/soon")
    @Requires(Role.READER)
    public List<PermitOut> expiringPermits(
            @RequestParam Optional<LocalDate> day, @RequestParam(defaultValue = "2") @Min(0) @Max(60) int within) {
        LocalDate start = day.orElseGet(LocalDate::now);
        return work.apply(uow -> shape(uow, uow.permits().expiring(start, start.plusDays(within))));
    }

    /** Kill a permit, with the reason on the record. */
    @PostMapping("/{number}/void")
    @Requires(Role.SUPERVISOR)
    public PermitOut voidPermit(
            @PathVariable String number,
            @RequestParam @Size(min = 3) String reason,
            @RequestParam Optional<LocalDate> on,
            Caller who) {
        LocalDate day = on.orElseGet(LocalDate::now);
        Permit permit = work.apply(uow -> Counter.voidPermit(uow, number, who.person(), day, reason));
        Logs.event("permit.voided", Facts.of("number", number, "by", who.person()));
        return PermitOut.of(permit);
    }

    /** What one carrier has out, newest first. */
    @GetMapping("/carrier/{usdot}")
    @Requires(Role.READER)
    public List<PermitOut> permitsForCarrier(@PathVariable int usdot) {
        return work.apply(uow -> shape(uow, uow.permits().forCarrier(usdot)));
    }

    /** Rows to permits to bodies. */
    private static List<PermitOut> shape(UnitOfWork uow, List<PermitRow> rows) {
        return rows.stream()
                .map(one -> PermitOut.of(uow.permits().get(one.getNumber())))
                .toList();
    }

    /** Log the line the office greps for, then shape the permit. */
    private static PermitOut issued(Permit permit, Caller who) {
        Logs.event(
                "permit.issued",
                Facts.of(
                        "number",
                        permit.number().toString(),
                        "usdot",
                        permit.usdot(),
                        "gross",
                        permit.gross().value(),
                        "miles",
                        permit.distance().roundedUp(),
                        "by",
                        who.person()));
        return PermitOut.of(permit);
    }
}
