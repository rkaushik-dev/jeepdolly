package io.github.rkaushikdev.jeepdolly.escorts;

import io.github.rkaushikdev.jeepdolly.Conflict;
import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Every pilot car company and every card the office has issued: who is allowed to pilot a load.
 *
 * <p>The escort table decides how many pilot cars a load needs. For twenty years it said nothing
 * about who they are. That changed with the certification order: an operator sits an examination,
 * holds a certificate for three years, and carries endorsements for the height pole and for police
 * liaison. The office keeps the register.
 *
 * <p>A certificate that has expired is kept. The register is the office's record of who has ever
 * been certified, because the renewal examination is shorter for somebody renewing than for
 * somebody sitting it fresh, and the only way to tell the two apart is to have kept the old card.
 */
public final class EscortRegister {

    /** How long a certificate runs from the day it is issued. */
    public static final int TERM_YEARS = 3;

    private final Map<String, Company> companies = new TreeMap<>();
    private final Map<String, Operator> operators = new TreeMap<>();

    /** @throws Conflict when a company with that ident is already on file */
    public Company addCompany(Company company) {
        if (companies.containsKey(company.ident())) {
            throw new Conflict("pilot car company " + company.ident() + " is already on file");
        }
        companies.put(company.ident(), company);
        return company;
    }

    /** @throws NotOnFile when no company with that ident is on file */
    public Company company(String ident) {
        Company found = companies.get(ident);
        if (found == null) {
            throw new NotOnFile("no pilot car company " + ident, Facts.of("ident", ident));
        }
        return found;
    }

    /** Every company, in ident order. */
    public List<Company> companies() {
        return List.copyOf(companies.values());
    }

    /**
     * Put a card on the register, refusing one with no company behind it.
     *
     * @throws Conflict when the card is already on file
     * @throws NotOnFile when the card's company is not on file
     */
    public Operator certify(Operator operator) {
        if (operators.containsKey(operator.card())) {
            throw new Conflict("card " + operator.card() + " is already on file");
        }
        company(operator.company());
        operators.put(operator.card(), operator);
        return operator;
    }

    /** @throws NotOnFile when no card with that number is on file */
    public Operator operator(String card) {
        Operator found = operators.get(card);
        if (found == null) {
            throw new NotOnFile("no pilot car card " + card, Facts.of("card", card));
        }
        return found;
    }

    /** Every card, in card order. */
    public List<Operator> operators() {
        return List.copyOf(operators.values());
    }

    /** The cards of one company, in card order. */
    public List<Operator> operators(String company) {
        return operators.values().stream()
                .filter(one -> one.company().equals(company))
                .toList();
    }

    /**
     * The one question a permit asks: can this card be named for that day.
     *
     * @throws NotOnFile when the card, or its company, is not on file
     */
    public Standing standing(String card, LocalDate day) {
        Operator operator = operator(card);
        Company company = company(operator.company());
        return operator.standing(day, company.suspended());
    }

    /** Everybody who can be named for that day, in card order. */
    public List<Operator> available(LocalDate day) {
        return availableWith(day, Optional.empty());
    }

    /** Everybody who can be named for that day and carries the endorsement, in card order. */
    public List<Operator> available(LocalDate day, Endorsement endorsement) {
        return availableWith(day, Optional.of(endorsement));
    }

    private List<Operator> availableWith(LocalDate day, Optional<Endorsement> endorsement) {
        List<Operator> found = new ArrayList<>();
        for (Operator one : operators.values()) {
            if (!standing(one.card(), day).available()) {
                continue;
            }
            if (endorsement.isPresent() && !one.carries(endorsement.get())) {
                continue;
            }
            found.add(one);
        }
        return List.copyOf(found);
    }

    /**
     * Cards that run out between two days, both included, soonest first.
     *
     * <p>A suspended card is left out. It is not going to be renewed on the ordinary cycle and the
     * letter that goes with a suspension is not the letter that goes with a renewal.
     */
    public List<Operator> expiring(LocalDate start, LocalDate end) {
        return operators.values().stream()
                .filter(one -> !one.suspended()
                        && !one.expires().isBefore(start)
                        && !one.expires().isAfter(end))
                .sorted(Comparator.comparing(Operator::expires).thenComparing(Operator::card))
                .toList();
    }

    /** How many cards are on the register. */
    public int size() {
        return operators.size();
    }

    /** Whether a card with that number is on the register. */
    public boolean contains(String card) {
        return operators.containsKey(card);
    }
}
