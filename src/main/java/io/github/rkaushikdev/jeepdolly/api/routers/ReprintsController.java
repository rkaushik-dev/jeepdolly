package io.github.rkaushikdev.jeepdolly.api.routers;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.Logs;
import io.github.rkaushikdev.jeepdolly.Unreadable;
import io.github.rkaushikdev.jeepdolly.Written;
import io.github.rkaushikdev.jeepdolly.api.Caller;
import io.github.rkaushikdev.jeepdolly.api.Rejected;
import io.github.rkaushikdev.jeepdolly.api.Requires;
import io.github.rkaushikdev.jeepdolly.api.Role;
import io.github.rkaushikdev.jeepdolly.api.Work;
import io.github.rkaushikdev.jeepdolly.permits.Permit;
import io.github.rkaushikdev.jeepdolly.permits.Printing;
import io.github.rkaushikdev.jeepdolly.permits.Reason;
import io.github.rkaushikdev.jeepdolly.permits.Reprint;
import io.github.rkaushikdev.jeepdolly.services.Audit;
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
 * Reprint routes: another copy of a permit, numbered, with the reason on the register.
 *
 * <p>A copy decides nothing about what is allowed and touches no permit record; the only thing it
 * shares with issuing is the number in the path. The rung of the ladder does the work: a clerk may
 * print up to the office's free limit and a supervisor may print past it, which is one rule
 * expressed as two roles rather than as a flag on the body.
 */
@RestController
@RequestMapping("/permits")
@Tag(name = "reprints")
public class ReprintsController {

    private final Work work;

    public ReprintsController(Work work) {
        this.work = work;
    }

    /** What the counter is told when somebody asks for another copy. */
    public record ReprintIn(
            @NotNull(message = Rejected.REQUIRED) String reason,
            @NotNull(message = Rejected.REQUIRED) String askedBy,
            @Size(max = 500) String note) {

        public ReprintIn {
            note = note == null ? "" : note;
        }

        /**
         * The reason, as the register records it.
         *
         * @throws Unreadable when it is not a reason this office records
         */
        public Reason kind() {
            try {
                return Reason.of(reason);
            } catch (IllegalArgumentException unknown) {
                throw new Unreadable(
                        Written.quoted(reason) + " is not a reason this office records", Facts.of("reason", reason));
            }
        }
    }

    /** The copy, and the sheet to put on the printer. */
    public record ReprintOut(
            String permit,
            int copyNumber,
            LocalDate issuedOn,
            String reason,
            String askedBy,
            String issuedBy,
            @JsonProperty("needed_a_supervisor") boolean neededASupervisor,
            String sheet) {

        public static ReprintOut of(Reprint made, String written, int freely) {
            return new ReprintOut(
                    made.permit(),
                    made.copy(),
                    made.on(),
                    made.reason().value(),
                    made.askedBy(),
                    made.issuedBy(),
                    made.neededASupervisor(freely),
                    written);
        }
    }

    /** One line of the register beside a permit. */
    public record CopyOut(
            int copyNumber, LocalDate issuedOn, String reason, String askedBy, String issuedBy, String note) {}

    private record Printed(Reprint made, String sheet) {}

    /** Print another copy, numbered, with the reason on the register. */
    @PostMapping("/{number}/copies")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires(Role.CLERK)
    public ReprintOut reprintPermit(
            @PathVariable String number,
            @Valid @RequestBody ReprintIn body,
            @RequestParam Optional<LocalDate> on,
            Caller who) {
        LocalDate day = on.orElseGet(LocalDate::now);
        int freely = work.rules().freeCopies();
        Printed printed = work.apply(uow -> {
            Permit permit = uow.permits().get(number);
            Reprint made = Reprint.issue(
                    permit,
                    day,
                    body.kind(),
                    body.askedBy(),
                    who.person(),
                    uow.reprints().copiesTaken(number),
                    who.role() == Role.SUPERVISOR,
                    body.note(),
                    freely);
            uow.reprints().add(made);
            Audit.record(
                    uow.entityManager(),
                    "permit.reprinted",
                    who.person(),
                    "permit",
                    number,
                    "copy " + made.copy() + ", " + made.reason());
            return new Printed(made, Printing.sheet(permit, made.copy()));
        });
        Logs.event(
                "permit.reprinted",
                Facts.of("number", number, "copy", printed.made().copy()));
        return ReprintOut.of(printed.made(), printed.sheet(), freely);
    }

    /** Every copy of a permit the office has handed out. */
    @GetMapping("/{number}/copies")
    @Requires(Role.READER)
    public List<CopyOut> copiesOf(@PathVariable String number) {
        return work.apply(uow -> {
            uow.permits().row(number);
            return uow.reprints().forPermit(number).stream()
                    .map(one -> new CopyOut(
                            one.getCopy(),
                            one.getIssuedOn(),
                            one.getReason().value(),
                            one.getAskedBy(),
                            one.getIssuedBy(),
                            one.getNote() == null ? "" : one.getNote()))
                    .toList();
        });
    }

    /** How many copies a clerk may print before a supervisor is wanted. */
    @GetMapping("/copies/free-limit")
    @Requires(Role.READER)
    public Map<String, Integer> freeLimit() {
        return Map.of("freely", work.rules().freeCopies());
    }
}
