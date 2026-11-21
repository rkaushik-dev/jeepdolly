package io.github.rkaushikdev.jeepdolly.cli;

import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.Unreadable;
import io.github.rkaushikdev.jeepdolly.Written;
import io.github.rkaushikdev.jeepdolly.escorts.Company;
import io.github.rkaushikdev.jeepdolly.escorts.Endorsement;
import io.github.rkaushikdev.jeepdolly.escorts.EscortRegister;
import io.github.rkaushikdev.jeepdolly.escorts.Operator;
import io.github.rkaushikdev.jeepdolly.persistence.Books;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.persistence.models.PilotCardRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.PilotCompanyRow;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Pilot car commands, which exist because of the renewal run.
 *
 * <p>Once a quarter somebody sits down with the register and rings everybody whose card runs out in
 * the next ninety days. That was a screen with no print button, so it was done by reading the screen
 * onto a pad, and the pad is how two people were missed in the spring.
 *
 * <p>So the renewal list is a command. It prints one card to a line with the telephone number of the
 * company on it, and it goes straight into a mail merge or straight onto a clipboard.
 *
 * <p>The rest of the group is here because once the office had one pilot car command they wanted the
 * others in the same place. Certifying from the command line is used exactly once a month, on the
 * morning after the examination, when there are eleven new cards to put on and doing that through a
 * form eleven times is how a wrong expiry date gets typed.
 */
final class PilotCommands {

    /** How far ahead the renewal run looks when nobody says otherwise. */
    static final int RENEWAL_DAYS = 90;

    private PilotCommands() {}

    /** One endorsement by its printed name, refusing anything else. */
    static Endorsement endorsement(String written) {
        try {
            return Endorsement.of(written);
        } catch (IllegalArgumentException unknown) {
            throw new Unreadable(
                    Written.quoted(written) + " is not an endorsement this office issues",
                    Facts.of("endorsement", written));
        }
    }

    /** The register off the rows, which the store already knows how to build. */
    private static EscortRegister register(UnitOfWork work) {
        return Books.escortsFrom(work.entityManager());
    }

    @Command(
            name = "pilots",
            description = "Pilot car companies, their cards, and the renewal run.",
            subcommands = {
                Companies.class,
                Cards.class,
                Expiring.class,
                AddCompany.class,
                Certify.class,
                Suspend.class,
                Available.class
            })
    static final class Pilots {}

    @Command(name = "companies", description = "Every pilot car company, with how many cards it holds.")
    static final class Companies extends Action {

        @Option(names = "--suspended", description = "Only the ones that are off.")
        private boolean suspended;

        @Override
        public Integer call() {
            try (UnitOfWork work = work()) {
                for (PilotCompanyRow row : work.pilots().companies()) {
                    if (suspended && !row.isSuspended()) {
                        continue;
                    }
                    String mark = row.isSuspended() ? "suspended" : "";
                    say(
                            row.getIdent(),
                            row.getName(),
                            row.getTown(),
                            row.getTelephone(),
                            row.getCards().size(),
                            mark);
                }
            }
            return Main.DONE;
        }
    }

    @Command(name = "cards", description = "Every card, with what it is good for on a day.")
    static final class Cards extends Action {

        @Option(names = "--company", description = "One company rather than all of them.")
        private Optional<String> company = Optional.empty();

        @Option(names = "--day")
        private Optional<LocalDate> day = Optional.empty();

        @Override
        public Integer call() {
            LocalDate when = day.orElseGet(LocalDate::now);
            try (UnitOfWork work = work()) {
                EscortRegister register = register(work);
                List<PilotCardRow> rows = company.map(work.pilots()::cards).orElseGet(work.pilots()::cards);
                for (PilotCardRow row : rows) {
                    String marks = row.toDomain().endorsements().stream()
                            .map(Endorsement::value)
                            .sorted()
                            .collect(Collectors.joining(","));
                    say(
                            row.getCard(),
                            row.getName(),
                            row.getCompanyIdent(),
                            row.getExpires(),
                            register.standing(row.getCard(), when),
                            marks);
                }
            }
            return Main.DONE;
        }
    }

    @Command(name = "expiring", description = "The renewal run: who to ring, soonest first, with the number.")
    static final class Expiring extends Action {

        @Option(names = "--day")
        private Optional<LocalDate> day = Optional.empty();

        @Option(names = "--within", defaultValue = "" + RENEWAL_DAYS, description = "Days ahead to look.")
        private int within;

        @Override
        public Integer call() {
            LocalDate when = day.orElseGet(LocalDate::now);
            List<PilotCardRow> found;
            try (UnitOfWork work = work()) {
                found = work.pilots().expiring(when, when.plusDays(within));
                for (PilotCardRow row : found) {
                    PilotCompanyRow company = work.pilots().companyRow(row.getCompanyIdent());
                    long days = ChronoUnit.DAYS.between(when, row.getExpires());
                    say(
                            row.getCard(),
                            row.getName(),
                            company.getName(),
                            company.getTelephone(),
                            row.getExpires(),
                            days);
                }
            }
            if (found.isEmpty()) {
                say("nothing runs out in that window");
            }
            return Main.DONE;
        }
    }

    @Command(name = "company", description = "Put a pilot car company on the register.")
    static final class AddCompany extends Action {

        @Parameters(index = "0", paramLabel = "IDENT")
        private String ident;

        @Parameters(index = "1", paramLabel = "NAME")
        private String name;

        @Option(names = "--town", defaultValue = "")
        private String town;

        @Option(names = "--telephone", defaultValue = "")
        private String telephone;

        @Override
        public Integer call() {
            try (UnitOfWork work = work()) {
                work.pilots().addCompany(new Company(ident, name, town, telephone));
                work.commit();
            }
            say("filed", ident, name);
            return Main.DONE;
        }
    }

    @Command(name = "certify", description = "Issue a card after the examination. A renewal comes through here too.")
    static final class Certify extends Action {

        @Parameters(index = "0", paramLabel = "CARD")
        private String card;

        @Parameters(index = "1", paramLabel = "NAME")
        private String name;

        @Parameters(index = "2", paramLabel = "COMPANY")
        private String company;

        @Option(names = "--issued", required = true)
        private LocalDate issued;

        @Option(names = "--expires", required = true)
        private LocalDate expires;

        @Option(names = "--endorsement", description = "May be given more than once.")
        private List<String> endorsements = new ArrayList<>();

        @Override
        public Integer call() {
            Set<Endorsement> marks =
                    endorsements.stream().map(PilotCommands::endorsement).collect(Collectors.toSet());
            Operator operator = new Operator(card, name, company, issued, expires, marks);
            try (UnitOfWork work = work()) {
                work.pilots().certify(operator);
                work.commit();
            }
            say("certified", card, name, company, expires);
            return Main.DONE;
        }
    }

    @Command(name = "suspend", description = "Stop an operator working, or let them back.")
    static final class Suspend extends Action {

        @Parameters(paramLabel = "CARD")
        private String card;

        @Option(names = "--lift", description = "Let them back rather than stop them.")
        private boolean lift;

        @Override
        public Integer call() {
            PilotCardRow row;
            try (UnitOfWork work = work()) {
                row = work.pilots().suspendCard(card, !lift);
                work.commit();
            }
            say(lift ? "lifted" : "suspended", card, row.getName());
            return Main.DONE;
        }
    }

    @Command(name = "available", description = "Everybody who can be named on a permit for a day.")
    static final class Available extends Action {

        @Option(names = "--day")
        private Optional<LocalDate> day = Optional.empty();

        @Option(names = "--endorsement", description = "Narrow it to one endorsement.")
        private Optional<String> endorsement = Optional.empty();

        @Override
        public Integer call() {
            LocalDate when = day.orElseGet(LocalDate::now);
            Optional<Endorsement> wanted =
                    endorsement.filter(given -> !given.isEmpty()).map(PilotCommands::endorsement);
            List<Operator> found;
            try (UnitOfWork work = work()) {
                EscortRegister register = register(work);
                found = wanted.map(mark -> register.available(when, mark)).orElseGet(() -> register.available(when));
                for (Operator one : found) {
                    Company company = register.company(one.company());
                    say(one.card(), one.name(), company.name(), company.telephone());
                }
            }
            if (found.isEmpty()) {
                complain("nobody is available that day");
            }
            return Main.DONE;
        }
    }
}
