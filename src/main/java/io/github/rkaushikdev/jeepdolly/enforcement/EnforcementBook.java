package io.github.rkaushikdev.jeepdolly.enforcement;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Every carrier that owes the office something, read as at one day. */
public final class EnforcementBook {

    private final Map<Integer, Account> accounts = new LinkedHashMap<>();

    /** Put a carrier's account in the book, replacing any account it had. */
    public Account add(Account account) {
        accounts.put(account.usdot(), account);
        return account;
    }

    /**
     * One carrier, which is an empty account when nothing is on file.
     *
     * <p>An empty account rather than a refusal. A carrier with no citations is the ordinary case
     * and making every caller handle a missing one would put the same two lines in six places.
     */
    public Account account(int usdot) {
        return accounts.getOrDefault(usdot, new Account(usdot));
    }

    /** The refusal line for a carrier on a day, or empty when it is clear to load. */
    public Optional<String> hold(int usdot, LocalDate day) {
        return account(usdot).hold(day);
    }

    /** Every carrier the office is holding, worst first, then by usdot number. */
    public List<Account> held(LocalDate day) {
        return accounts.values().stream()
                .filter(one -> one.hold(day).isPresent())
                .sorted(Comparator.comparingInt(
                                (Account one) -> -one.pastDue(day).size())
                        .thenComparingInt(Account::usdot))
                .toList();
    }

    public int size() {
        return accounts.size();
    }

    public boolean contains(int usdot) {
        return accounts.containsKey(usdot);
    }
}
