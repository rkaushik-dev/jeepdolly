package io.github.rkaushikdev.jeepdolly.api.routers;

import io.github.rkaushikdev.jeepdolly.Unreadable;
import io.github.rkaushikdev.jeepdolly.Written;
import io.github.rkaushikdev.jeepdolly.api.Caller;
import io.github.rkaushikdev.jeepdolly.api.Deps;
import io.github.rkaushikdev.jeepdolly.api.Requires;
import io.github.rkaushikdev.jeepdolly.api.Role;
import io.github.rkaushikdev.jeepdolly.api.Schemas.ApplicationIn;
import io.github.rkaushikdev.jeepdolly.api.Schemas.ApplicationOut;
import io.github.rkaushikdev.jeepdolly.api.Schemas.AssessmentOut;
import io.github.rkaushikdev.jeepdolly.api.Schemas.ConditionOut;
import io.github.rkaushikdev.jeepdolly.api.Schemas.QuoteOut;
import io.github.rkaushikdev.jeepdolly.api.Work;
import io.github.rkaushikdev.jeepdolly.permits.Application;
import io.github.rkaushikdev.jeepdolly.permits.ApplicationState;
import io.github.rkaushikdev.jeepdolly.permits.Assessment;
import io.github.rkaushikdev.jeepdolly.routing.Itinerary;
import io.github.rkaushikdev.jeepdolly.services.Counter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
 * Application routes: file one, read it, move it through the queue, and ask what the office makes
 * of it.
 *
 * <p>The order of the office procedure lives in {@link Counter}; what is left here is the rung of
 * the ladder each route needs, the body it reads, the day it is deciding for, and the shape that
 * goes back. Assessing is a GET, because asking what a load would cost changes nothing and a
 * dispatcher asks it four times while a hauler holds the line.
 */
@RestController
@RequestMapping("/applications")
@Tag(name = "applications")
public class ApplicationsController {

    private final Work work;

    public ApplicationsController(Work work) {
        this.work = work;
    }

    /** Take an application in and give it a reference. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Requires(Role.CLERK)
    public ApplicationOut fileApplication(
            @Valid @RequestBody ApplicationIn body, @RequestParam Optional<LocalDate> filed, Caller who) {
        LocalDate day = filed.orElseGet(LocalDate::now);
        return work.apply(uow -> {
            String reference = Counter.nextReference(uow, day);
            Application application =
                    body.toDomain(reference, day, uow.commodities().book(day));
            Counter.fileApplication(uow, application, who.person(), day);
            return ApplicationOut.of(application);
        });
    }

    /** One application as it was submitted. */
    @GetMapping("/{reference}")
    @Requires(Role.READER)
    public ApplicationOut readApplication(@PathVariable String reference) {
        return work.apply(uow -> ApplicationOut.of(uow.applications().get(reference)));
    }

    /**
     * The queue, by state, oldest first.
     *
     * @throws Unreadable when the state is not one an application can be in
     */
    @GetMapping
    @Requires(Role.READER)
    public List<ApplicationOut> listApplications(@RequestParam(defaultValue = "submitted") String state) {
        ApplicationState wanted;
        try {
            wanted = ApplicationState.of(state);
        } catch (IllegalArgumentException unknown) {
            throw new Unreadable(Written.quoted(state) + " is not a state an application can be in");
        }
        return work.apply(uow -> uow.applications().inState(wanted).stream()
                .map(row -> ApplicationOut.of(uow.applications().get(row.getReference())))
                .toList());
    }

    /** Move an application out of draft and into the queue. */
    @PostMapping("/{reference}/submit")
    @Requires(Role.CLERK)
    public ApplicationOut submitApplication(
            @PathVariable String reference, @RequestParam Optional<LocalDate> on, Caller who) {
        LocalDate day = on.orElseGet(LocalDate::now);
        return work.apply(uow -> ApplicationOut.of(Counter.submit(uow, reference, who.person(), day)));
    }

    /** Name the pilot cars, or change them, which happens more than once. */
    @PostMapping("/{reference}/pilots")
    @Requires(Role.CLERK)
    public ApplicationOut namePilots(
            @PathVariable String reference,
            @RequestBody List<String> cards,
            @RequestParam Optional<LocalDate> on,
            Caller who) {
        LocalDate day = on.orElseGet(LocalDate::now);
        return work.apply(uow -> ApplicationOut.of(
                Counter.namePilots(uow, Deps.officeFrom(uow, day), reference, List.copyOf(cards), who.person(), day)));
    }

    /** The carrier rang up and cancelled. */
    @PostMapping("/{reference}/withdraw")
    @Requires(Role.CLERK)
    public ApplicationOut withdrawApplication(
            @PathVariable String reference, @RequestParam Optional<LocalDate> on, Caller who) {
        LocalDate day = on.orElseGet(LocalDate::now);
        return work.apply(uow -> ApplicationOut.of(Counter.withdraw(uow, reference, who.person(), day)));
    }

    /** Close an application the office will not write, with the reason. */
    @PostMapping("/{reference}/refuse")
    @Requires(Role.CLERK)
    public ApplicationOut refuseApplication(
            @PathVariable String reference,
            @RequestParam @Size(min = 3) String reason,
            @RequestParam Optional<LocalDate> on,
            Caller who) {
        LocalDate day = on.orElseGet(LocalDate::now);
        return work.apply(uow -> ApplicationOut.of(Counter.refuse(uow, reference, who.person(), day, reason)));
    }

    /** What the office makes of it, without deciding anything yet: as of its first day unless asked. */
    @GetMapping("/{reference}/assessment")
    @Requires(Role.READER)
    public AssessmentOut assessApplication(@PathVariable String reference, @RequestParam Optional<LocalDate> on) {
        return work.apply(uow -> {
            Application application = uow.applications().get(reference);
            LocalDate day = on.orElse(application.start());
            return shape(application, Counter.lookAt(Deps.officeFrom(uow, day), application, day));
        });
    }

    /** The assessment as it goes over the wire. */
    public static AssessmentOut shape(Application application, Assessment found) {
        return new AssessmentOut(
                application.reference(),
                found.permittable(),
                found.kind().value(),
                found.refusals(),
                found.itinerary().map(Itinerary::idents).orElse(List.of()),
                found.itinerary().map(one -> one.distance().toString()),
                found.conditions().stream().map(ConditionOut::of).toList(),
                found.quote().map(QuoteOut::of));
    }
}
