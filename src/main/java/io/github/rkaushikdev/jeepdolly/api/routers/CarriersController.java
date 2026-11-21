package io.github.rkaushikdev.jeepdolly.api.routers;

import io.github.rkaushikdev.jeepdolly.Dollars;
import io.github.rkaushikdev.jeepdolly.api.Caller;
import io.github.rkaushikdev.jeepdolly.api.Rejected;
import io.github.rkaushikdev.jeepdolly.api.Requires;
import io.github.rkaushikdev.jeepdolly.api.Role;
import io.github.rkaushikdev.jeepdolly.api.Work;
import io.github.rkaushikdev.jeepdolly.domain.Billing;
import io.github.rkaushikdev.jeepdolly.domain.Bond;
import io.github.rkaushikdev.jeepdolly.domain.Carrier;
import io.github.rkaushikdev.jeepdolly.domain.Insurance;
import io.github.rkaushikdev.jeepdolly.permits.Permit;
import io.github.rkaushikdev.jeepdolly.permits.PermitState;
import io.github.rkaushikdev.jeepdolly.persistence.models.BondRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.CarrierRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.InsuranceRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.PermitRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.SuspensionRow;
import io.github.rkaushikdev.jeepdolly.services.Audit;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
 * Carrier routes: register one, file its paper, suspend it, let it back.
 *
 * <p>Suspending a carrier is the one route in the service with a side effect on documents it was
 * not asked about. A suspension voids every live permit the carrier holds, because a suspended
 * carrier is not entitled to be on the road under a permit this office wrote yesterday. That is done
 * here rather than left to the supervisor to remember, and every voided permit lands on the trail
 * with the suspension as its reason.
 */
@RestController
@RequestMapping("/carriers")
@Tag(name = "carriers")
public class CarriersController {

    /** What a carrier is told it is when nothing stands in its way. */
    static final String GOOD_STANDING = "in good standing";

    private final Work work;

    public CarriersController(Work work) {
        this.work = work;
    }

    /** A carrier as the counter types it in. Billing left out is at the counter. */
    public record CarrierIn(
            @NotNull(message = Rejected.REQUIRED) Integer usdot,
            @NotNull(message = Rejected.REQUIRED) String name,
            @NotNull(message = Rejected.REQUIRED) String town,
            @NotNull(message = Rejected.REQUIRED) String state,
            String billing) {

        public CarrierIn {
            billing = billing == null ? Billing.COUNTER.value() : billing;
        }

        public Carrier toDomain() {
            return Carrier.builder(usdot, name, town, state)
                    .billing(Billing.of(billing))
                    .build();
        }
    }

    /** A certificate as it arrives by fax. */
    public record InsuranceIn(
            @NotNull(message = Rejected.REQUIRED) String policy,
            @NotNull(message = Rejected.REQUIRED) String insurer,
            @NotNull(message = Rejected.REQUIRED) String liability,
            @NotNull(message = Rejected.REQUIRED) LocalDate effective,
            @NotNull(message = Rejected.REQUIRED) LocalDate expires) {

        public Insurance toDomain() {
            return new Insurance(policy, insurer, Dollars.parse(liability), effective, expires);
        }
    }

    /** A surety bond, for the heavy end of the work. */
    public record BondIn(
            @NotNull(message = Rejected.REQUIRED) String number,
            @NotNull(message = Rejected.REQUIRED) String surety,
            @NotNull(message = Rejected.REQUIRED) String amount,
            @NotNull(message = Rejected.REQUIRED) LocalDate effective,
            @NotNull(message = Rejected.REQUIRED) LocalDate expires) {

        public Bond toDomain() {
            return new Bond(number, surety, Dollars.parse(amount), effective, expires);
        }
    }

    /**
     * A carrier and where it stands on a day.
     *
     * @param standing what stops a permit for that day, or {@code in good standing}
     */
    public record CarrierOut(int usdot, String name, String town, String state, String billing, String standing) {

        public static CarrierOut of(CarrierRow row, LocalDate on) {
            Carrier carrier = row.toDomain(on);
            return new CarrierOut(
                    row.getUsdot(),
                    row.getName(),
                    row.getTown(),
                    row.getState(),
                    row.getBilling().value(),
                    carrier.standing(on, on).orElse(GOOD_STANDING));
        }
    }

    /** Put a carrier on file. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Requires(Role.CLERK)
    public CarrierOut registerCarrier(
            @Valid @RequestBody CarrierIn body, @RequestParam Optional<LocalDate> on, Caller who) {
        LocalDate day = on.orElseGet(LocalDate::now);
        return work.apply(uow -> {
            CarrierRow row = uow.carriers().add(body.toDomain());
            Audit.record(
                    uow.entityManager(), "carrier.registered", who.person(), "carrier", String.valueOf(body.usdot()));
            return CarrierOut.of(row, day);
        });
    }

    /** One carrier, and where it stands on a day. */
    @GetMapping("/{usdot}")
    @Requires(Role.READER)
    public CarrierOut readCarrier(@PathVariable int usdot, @RequestParam Optional<LocalDate> on) {
        LocalDate day = on.orElseGet(LocalDate::now);
        return work.apply(uow -> CarrierOut.of(uow.carriers().row(usdot), day));
    }

    /** Name search, the way the counter does it. */
    @GetMapping
    @Requires(Role.READER)
    public List<CarrierOut> searchCarriers(@RequestParam String name, @RequestParam Optional<LocalDate> on) {
        LocalDate day = on.orElseGet(LocalDate::now);
        return work.apply(uow -> uow.carriers().search(name).stream()
                .map(one -> CarrierOut.of(one, day))
                .toList());
    }

    /** File a certificate. A renewal is a new row, never an edit. */
    @PostMapping("/{usdot}/insurance")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires(Role.CLERK)
    public CarrierOut fileInsurance(
            @PathVariable int usdot, @Valid @RequestBody InsuranceIn body, @RequestParam Optional<LocalDate> on) {
        LocalDate day = on.orElseGet(LocalDate::now);
        return work.apply(uow -> {
            CarrierRow row = uow.carriers().row(usdot);
            row.addCertificate(InsuranceRow.of(usdot, body.toDomain(), day));
            return CarrierOut.of(row, day);
        });
    }

    /** File a bond. */
    @PostMapping("/{usdot}/bond")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires(Role.CLERK)
    public CarrierOut fileBond(
            @PathVariable int usdot, @Valid @RequestBody BondIn body, @RequestParam Optional<LocalDate> on) {
        LocalDate day = on.orElseGet(LocalDate::now);
        return work.apply(uow -> {
            CarrierRow row = uow.carriers().row(usdot);
            row.addBond(BondRow.of(usdot, body.toDomain()));
            return CarrierOut.of(row, day);
        });
    }

    /**
     * Stop writing permits for somebody, and kill the ones they hold.
     *
     * <p>Every permit the carrier holds that is issued and not yet run out is voided with the
     * suspension as its reason, and each lands on the trail.
     */
    @PostMapping("/{usdot}/suspend")
    @Requires(Role.SUPERVISOR)
    public CarrierOut suspendCarrier(
            @PathVariable int usdot,
            @RequestParam @Size(min = 3) String reason,
            @RequestParam Optional<LocalDate> on,
            @RequestParam Optional<LocalDate> until,
            Caller who) {
        LocalDate day = on.orElseGet(LocalDate::now);
        return work.apply(uow -> {
            CarrierRow row = uow.carriers().row(usdot);
            SuspensionRow order = row.addSuspension(new SuspensionRow(usdot, reason, day));
            order.setUntil(until.orElse(null));
            order.setOrderedBy(who.person());
            Audit.record(
                    uow.entityManager(), "carrier.suspended", who.person(), "carrier", String.valueOf(usdot), reason);
            for (PermitRow held : uow.permits().forCarrier(usdot)) {
                Permit permit = uow.permits().get(held.getNumber());
                if (permit.state() == PermitState.ISSUED && !permit.expiredOn(day)) {
                    permit.makeVoid(day, "carrier suspended: " + reason);
                    uow.permits().saveState(permit);
                    Audit.record(
                            uow.entityManager(), "permit.voided", who.person(), "permit", held.getNumber(), reason);
                }
            }
            return CarrierOut.of(row, day);
        });
    }

    /** Lift a suspension. The permits it killed stay dead. */
    @PostMapping("/{usdot}/restore")
    @Requires(Role.SUPERVISOR)
    public CarrierOut restoreCarrier(@PathVariable int usdot, @RequestParam Optional<LocalDate> on, Caller who) {
        LocalDate day = on.orElseGet(LocalDate::now);
        return work.apply(uow -> {
            CarrierRow row = uow.carriers().row(usdot);
            for (SuspensionRow order : row.getSuspensions()) {
                if (order.getUntil().isEmpty() || order.getUntil().get().isAfter(day)) {
                    order.setUntil(day);
                }
            }
            Audit.record(uow.entityManager(), "carrier.restored", who.person(), "carrier", String.valueOf(usdot));
            return CarrierOut.of(row, day);
        });
    }
}
