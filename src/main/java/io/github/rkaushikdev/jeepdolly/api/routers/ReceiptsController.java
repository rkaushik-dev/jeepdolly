package io.github.rkaushikdev.jeepdolly.api.routers;

import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.api.Caller;
import io.github.rkaushikdev.jeepdolly.api.Rejected;
import io.github.rkaushikdev.jeepdolly.api.Requires;
import io.github.rkaushikdev.jeepdolly.api.Role;
import io.github.rkaushikdev.jeepdolly.api.Work;
import io.github.rkaushikdev.jeepdolly.services.Audit;
import io.github.rkaushikdev.jeepdolly.services.Till;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.LinkedHashMap;
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
 * Receipt routes: taking money against a permit, what is left on one, and the drawer at half past
 * four, with the cash split from the account work the way the clerk counts it.
 */
@RestController
@RequestMapping("/receipts")
@Tag(name = "receipts")
public class ReceiptsController {

    private final Work work;

    public ReceiptsController(Work work) {
        this.work = work;
    }

    /** A payment as the clerk types it. */
    public record ReceiptIn(
            @NotNull(message = Rejected.REQUIRED) String permit,
            @NotNull(message = Rejected.REQUIRED) String amount,
            @NotNull(message = Rejected.REQUIRED) String method,
            String reference,
            String note) {

        public ReceiptIn {
            reference = reference == null ? "" : reference;
            note = note == null ? "" : note;
        }
    }

    /** One permit and what is left on it. */
    public record OwingOut(
            String permit,
            String carrier,
            LocalDate issued,
            String charged,
            String paid,
            String owed,
            boolean settled) {

        static OwingOut of(Till.Owing found) {
            return new OwingOut(
                    found.permit(),
                    found.carrier(),
                    found.issued(),
                    found.charged().toString(),
                    found.paid().toString(),
                    found.owed().toString(),
                    found.settled());
        }
    }

    /** What was taken on a day, split the way the clerk counts it. */
    public record DrawerOut(
            LocalDate day, String inTheDrawer, String onAccount, String taken, int receipts, List<String> lines) {

        public DrawerOut {
            lines = List.copyOf(lines);
        }
    }

    /** Write a receipt against a permit and say what is left on it. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Requires(Role.CLERK)
    public OwingOut takePayment(@Valid @RequestBody ReceiptIn body, @RequestParam Optional<LocalDate> on, Caller who) {
        LocalDate day = on.orElseGet(LocalDate::now);
        return work.apply(uow -> {
            Till.take(
                    uow.entityManager(),
                    body.permit(),
                    Dollars.parse(body.amount()),
                    body.method(),
                    who.person(),
                    day,
                    body.reference(),
                    body.note());
            Audit.record(
                    uow.entityManager(),
                    "payment.taken",
                    who.person(),
                    "permit",
                    body.permit(),
                    body.method() + " " + body.amount());
            return OwingOut.of(Till.owingOn(uow.entityManager(), body.permit()));
        });
    }

    /** What one permit was charged and what is left on it. */
    @GetMapping("/owing/{number}")
    @Requires(Role.READER)
    public OwingOut owingOnPermit(@PathVariable String number) {
        return work.apply(uow -> OwingOut.of(Till.owingOn(uow.entityManager(), number)));
    }

    /** Everything issued and not settled, oldest first. */
    @GetMapping("/outstanding")
    @Requires(Role.READER)
    public List<OwingOut> outstandingPermits(@RequestParam Optional<LocalDate> day) {
        LocalDate when = day.orElseGet(LocalDate::now);
        return work.apply(uow -> Till.outstanding(uow.entityManager(), when).stream()
                .map(OwingOut::of)
                .toList());
    }

    /** One number for one carrier, which is what the letter says. */
    @GetMapping("/carrier/{usdot}")
    @Requires(Role.READER)
    public Map<String, Object> owedByCarrier(@PathVariable int usdot, @RequestParam Optional<LocalDate> day) {
        LocalDate when = day.orElseGet(LocalDate::now);
        return work.apply(uow -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("usdot", usdot);
            body.put(
                    "owed", Till.owedByCarrier(uow.entityManager(), usdot, when).toString());
            return body;
        });
    }

    /** What is in the drawer at half past four. */
    @GetMapping("/drawer")
    @Requires(Role.CLERK)
    public DrawerOut drawer(@RequestParam Optional<LocalDate> day) {
        LocalDate when = day.orElseGet(LocalDate::now);
        return work.apply(uow -> {
            Till.Drawer found = Till.drawer(uow.entityManager(), when);
            return new DrawerOut(
                    found.day(),
                    found.inTheDrawer().toString(),
                    found.onAccount().toString(),
                    found.taken().toString(),
                    found.receipts(),
                    found.lines());
        });
    }
}
