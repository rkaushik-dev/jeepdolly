package io.github.rkaushikdev.jeepdolly.api.routers;

import io.github.rkaushikdev.jeepdolly.SeasonKind;
import io.github.rkaushikdev.jeepdolly.SeasonOrder;
import io.github.rkaushikdev.jeepdolly.api.Caller;
import io.github.rkaushikdev.jeepdolly.api.Rejected;
import io.github.rkaushikdev.jeepdolly.api.Requires;
import io.github.rkaushikdev.jeepdolly.api.Role;
import io.github.rkaushikdev.jeepdolly.api.Work;
import io.github.rkaushikdev.jeepdolly.persistence.SeasonStore;
import io.github.rkaushikdev.jeepdolly.persistence.models.SeasonOrderRow;
import io.github.rkaushikdev.jeepdolly.services.Audit;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
 * Seasonal order routes, and the day the routing starts reading them.
 *
 * <p>The office reads every order on file when it assembles itself, so a posted road under an order
 * comes back as a condition on every assessment that touches it.
 *
 * <p>Filing an order is a supervisor's job. The district engineer signs the paper and the supervisor
 * types it, and the number on the row is the number on the paper.
 *
 * <p>Lifting one early is the same job. An order that lifts on the Tuesday because the frost came
 * out is ended on the Tuesday, and the row stays with that date on it, because a permit written on
 * the Monday was priced against it.
 */
@RestController
@RequestMapping("/seasons")
@Tag(name = "seasons")
public class SeasonsController {

    private final Work work;

    public SeasonsController(Work work) {
        this.work = work;
    }

    /**
     * An order as the district signed it.
     *
     * @param share the percentage of the ordinary limit the order allows
     */
    public record OrderIn(
            @NotNull(message = Rejected.REQUIRED) String number,
            @NotNull(message = Rejected.REQUIRED) String region,
            @NotNull(message = Rejected.REQUIRED) String kind,
            @NotNull(message = Rejected.REQUIRED) LocalDate start,
            @NotNull(message = Rejected.REQUIRED) LocalDate end,
            @NotNull(message = Rejected.REQUIRED) Integer share,
            String signedBy,
            String note) {

        public OrderIn {
            signedBy = signedBy == null ? "" : signedBy;
            note = note == null ? "" : note;
        }

        /**
         * The order this body describes.
         *
         * @throws IllegalArgumentException when the kind is not one an order can be
         * @throws io.github.rkaushikdev.jeepdolly.Impossible when the order cannot exist as written
         */
        public SeasonOrder toDomain() {
            return new SeasonOrder(number, region, SeasonKind.of(kind), start, end, share);
        }
    }

    /** An order as the office holds it. */
    public record OrderOut(
            String number, String region, String kind, LocalDate start, LocalDate end, int share, String signedBy) {

        public static OrderOut of(SeasonOrderRow row) {
            SeasonOrder order = row.toDomain();
            return new OrderOut(
                    order.number(),
                    order.region(),
                    order.kind().value(),
                    order.start(),
                    order.end(),
                    order.share(),
                    row.getSignedBy());
        }
    }

    /** Put a signed order on file, refusing an overlap. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Requires(Role.SUPERVISOR)
    public OrderOut fileSeasonOrder(@Valid @RequestBody OrderIn body, Caller who) {
        return work.apply(uow -> {
            SeasonOrderRow row =
                    SeasonStore.fileOrder(uow.entityManager(), body.toDomain(), body.signedBy(), body.note());
            Audit.record(
                    uow.entityManager(),
                    SeasonKind.THAW.value().equals(body.kind()) ? "road.closed" : "road.opened",
                    who.person(),
                    "season",
                    body.number(),
                    body.region() + " at " + body.share() + "%");
            return OrderOut.of(row);
        });
    }

    /** One order, by the number on the paper. */
    @GetMapping("/{number}")
    @Requires(Role.READER)
    public OrderOut readOrder(@PathVariable String number) {
        return work.apply(uow -> OrderOut.of(SeasonStore.rowFor(uow.entityManager(), number)));
    }

    /** Every order a region has had, in the order they ran. */
    @GetMapping
    @Requires(Role.READER)
    public List<OrderOut> ordersForRegion(@RequestParam String region) {
        return work.apply(uow -> SeasonStore.forRegion(uow.entityManager(), region).stream()
                .map(OrderOut::of)
                .toList());
    }

    /**
     * What is in force in a region on a day, if anything is. Nothing in force is answered as a JSON
     * {@code null}, not as an empty body.
     */
    @GetMapping("/in-force/{region}")
    @Requires(Role.READER)
    public Optional<OrderOut> orderInForce(@PathVariable String region, @RequestParam Optional<LocalDate> day) {
        LocalDate when = day.orElseGet(LocalDate::now);
        return work.apply(
                uow -> SeasonStore.inForce(uow.entityManager(), region, when).map(OrderOut::of));
    }

    /** End an order early, keeping the row and the day it ended. */
    @PostMapping("/{number}/lift")
    @Requires(Role.SUPERVISOR)
    public OrderOut liftOrder(@PathVariable String number, @RequestParam Optional<LocalDate> on, Caller who) {
        LocalDate day = on.orElseGet(LocalDate::now);
        return work.apply(uow -> {
            SeasonOrderRow row = SeasonStore.lift(uow.entityManager(), number, day);
            Audit.record(uow.entityManager(), "road.opened", who.person(), "season", number, day.toString());
            return OrderOut.of(row);
        });
    }
}
