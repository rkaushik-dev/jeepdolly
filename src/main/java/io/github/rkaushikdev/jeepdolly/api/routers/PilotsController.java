package io.github.rkaushikdev.jeepdolly.api.routers;

import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.Unreadable;
import io.github.rkaushikdev.jeepdolly.Written;
import io.github.rkaushikdev.jeepdolly.api.Caller;
import io.github.rkaushikdev.jeepdolly.api.Rejected;
import io.github.rkaushikdev.jeepdolly.api.Requires;
import io.github.rkaushikdev.jeepdolly.api.Role;
import io.github.rkaushikdev.jeepdolly.api.Work;
import io.github.rkaushikdev.jeepdolly.escorts.Company;
import io.github.rkaushikdev.jeepdolly.escorts.Endorsement;
import io.github.rkaushikdev.jeepdolly.escorts.Operator;
import io.github.rkaushikdev.jeepdolly.persistence.models.PilotCardRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.PilotCompanyRow;
import io.github.rkaushikdev.jeepdolly.persistence.repositories.PilotRepository;
import io.github.rkaushikdev.jeepdolly.services.Audit;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
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
 * Pilot car routes: the companies, the cards, and the two suspensions.
 *
 * <p>There is no route that renews a card, and there will not be one. A renewal is a new card with
 * a new number, issued by the same route that issues a first one, and a route that moved an expiry
 * date would quietly destroy the only record of what was in force on the day of a move.
 *
 * <p>Suspending is two routes rather than one with a flag, because they are two different decisions
 * made by two different people. A company is suspended when its liability insurance lapses, which
 * the supervisor does. An operator is suspended after an incident, which the supervisor also does
 * but on a different piece of paper and usually months apart.
 *
 * <p>Lifting either one is the same route with {@code suspended=false}, which is not elegant and is
 * what the counter asked for: they wanted one screen with a switch on it, not a suspend button and a
 * reinstate button side by side.
 */
@RestController
@RequestMapping("/pilots")
@Tag(name = "pilots")
public class PilotsController {

    private final Work work;

    public PilotsController(Work work) {
        this.work = work;
    }

    /** A pilot car company as the counter types it in. */
    public record CompanyIn(
            @NotNull(message = Rejected.REQUIRED) String ident,
            @NotNull(message = Rejected.REQUIRED) String name,
            @NotNull(message = Rejected.REQUIRED) String town,
            String telephone,
            String note) {

        public CompanyIn {
            telephone = telephone == null ? "" : telephone;
            note = note == null ? "" : note;
        }

        public Company toDomain() {
            return new Company(ident, name, town, telephone, false, note);
        }
    }

    /** A company as the office holds it. */
    public record CompanyOut(String ident, String name, String town, String telephone, boolean suspended, int cards) {

        public static CompanyOut of(PilotCompanyRow row) {
            return new CompanyOut(
                    row.getIdent(),
                    row.getName(),
                    row.getTown(),
                    row.getTelephone(),
                    row.isSuspended(),
                    row.getCards().size());
        }
    }

    /** A certificate as it is issued after the examination. */
    public record CardIn(
            @NotNull(message = Rejected.REQUIRED) String card,
            @NotNull(message = Rejected.REQUIRED) String name,
            @NotNull(message = Rejected.REQUIRED) String company,
            @NotNull(message = Rejected.REQUIRED) LocalDate issued,
            @NotNull(message = Rejected.REQUIRED) LocalDate expires,
            List<String> endorsements) {

        public CardIn {
            endorsements = endorsements == null ? List.of() : List.copyOf(endorsements);
        }

        /**
         * The operator this card describes.
         *
         * @throws Unreadable when an endorsement is not one this office issues
         */
        public Operator toDomain() {
            return new Operator(
                    card,
                    name,
                    company,
                    issued,
                    expires,
                    endorsements.stream().map(PilotsController::endorsement).collect(Collectors.toSet()));
        }
    }

    /**
     * One endorsement by its printed name, refusing anything else.
     *
     * <p>The store drops an endorsement it does not know, because rows are written before the code
     * that reads them ships. Coming the other way there is no such excuse: a clerk typing one that
     * does not exist has made a mistake and wants to be told so.
     *
     * @throws Unreadable when it is not an endorsement this office issues
     */
    static Endorsement endorsement(String written) {
        try {
            return Endorsement.of(written);
        } catch (IllegalArgumentException unknown) {
            throw new Unreadable(
                    Written.quoted(written) + " is not an endorsement this office issues",
                    Facts.of("endorsement", written));
        }
    }

    /** A card as the office holds it, its endorsements sorted by name. */
    public record CardOut(
            String card,
            String name,
            String company,
            LocalDate issued,
            LocalDate expires,
            List<String> endorsements,
            boolean suspended) {

        public CardOut {
            endorsements = List.copyOf(endorsements);
        }

        public static CardOut of(PilotCardRow row) {
            Operator operator = row.toDomain();
            return new CardOut(
                    operator.card(),
                    operator.name(),
                    operator.company(),
                    operator.issued(),
                    operator.expires(),
                    operator.endorsements().stream()
                            .map(Endorsement::value)
                            .sorted()
                            .toList(),
                    operator.suspended());
        }
    }

    /** Every pilot car company, in ident order. */
    @GetMapping("/companies")
    @Requires(Role.READER)
    public List<CompanyOut> listCompanies() {
        return work.apply(
                uow -> uow.pilots().companies().stream().map(CompanyOut::of).toList());
    }

    /** Put a pilot car company on the register. */
    @PostMapping("/companies")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires(Role.CLERK)
    public CompanyOut addCompany(@Valid @RequestBody CompanyIn body, Caller who) {
        return work.apply(uow -> {
            PilotCompanyRow row = uow.pilots().addCompany(body.toDomain());
            Audit.record(uow.entityManager(), "pilot.company_filed", who.person(), "pilot company", body.ident());
            return CompanyOut.of(row);
        });
    }

    /** One company, with how many cards are on its books. */
    @GetMapping("/companies/{ident}")
    @Requires(Role.READER)
    public CompanyOut readCompany(@PathVariable String ident) {
        return work.apply(uow -> CompanyOut.of(uow.pilots().companyRow(ident)));
    }

    /** Suspend a company, or let it back, which is the same switch. */
    @PostMapping("/companies/{ident}/suspension")
    @Requires(Role.SUPERVISOR)
    public CompanyOut suspendCompany(
            @PathVariable String ident,
            @RequestParam(defaultValue = "true") boolean suspended,
            @RequestParam(defaultValue = "") @Size(max = 200) String reason,
            Caller who) {
        return work.apply(uow -> {
            PilotCompanyRow row = uow.pilots().suspendCompany(ident, suspended);
            String action = suspended ? "pilot.company_suspended" : "pilot.company_restored";
            Audit.record(uow.entityManager(), action, who.person(), "pilot company", ident, reason);
            return CompanyOut.of(row);
        });
    }

    /** Every card, or every card of one company. */
    @GetMapping("/cards")
    @Requires(Role.READER)
    public List<CardOut> listCards(@RequestParam Optional<String> company) {
        return work.apply(uow -> {
            PilotRepository pilots = uow.pilots();
            List<PilotCardRow> found = company.isPresent() ? pilots.cards(company.get()) : pilots.cards();
            return found.stream().map(CardOut::of).toList();
        });
    }

    /** Issue a card after the examination. A renewal comes through here too. */
    @PostMapping("/cards")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires(Role.CLERK)
    public CardOut certify(@Valid @RequestBody CardIn body, Caller who) {
        return work.apply(uow -> {
            PilotCardRow row = uow.pilots().certify(body.toDomain());
            Audit.record(uow.entityManager(), "pilot.card_issued", who.person(), "pilot card", body.card());
            return CardOut.of(row);
        });
    }

    /** One card, as it stands. */
    @GetMapping("/cards/{card}")
    @Requires(Role.READER)
    public CardOut readCard(@PathVariable String card) {
        return work.apply(uow -> CardOut.of(uow.pilots().cardRow(card)));
    }

    /** Suspend an operator, or let them back. */
    @PostMapping("/cards/{card}/suspension")
    @Requires(Role.SUPERVISOR)
    public CardOut suspendCard(
            @PathVariable String card,
            @RequestParam(defaultValue = "true") boolean suspended,
            @RequestParam(defaultValue = "") @Size(max = 200) String reason,
            Caller who) {
        return work.apply(uow -> {
            PilotCardRow row = uow.pilots().suspendCard(card, suspended);
            String action = suspended ? "pilot.card_suspended" : "pilot.card_restored";
            Audit.record(uow.entityManager(), action, who.person(), "pilot card", card, reason);
            return CardOut.of(row);
        });
    }

    /** The cards running out between two days, soonest first. */
    @GetMapping("/expiring")
    @Requires(Role.READER)
    public List<CardOut> expiring(@RequestParam LocalDate start, @RequestParam LocalDate end) {
        return work.apply(uow ->
                uow.pilots().expiring(start, end).stream().map(CardOut::of).toList());
    }
}
