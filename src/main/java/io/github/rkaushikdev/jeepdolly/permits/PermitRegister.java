package io.github.rkaushikdev.jeepdolly.permits;

import io.github.rkaushikdev.jeepdolly.Conflict;
import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every permit the office has issued, and the four questions the telephone asks of it.
 *
 * <p>What is permit 25-P-004471: by number, and it has to work for a number read badly off a fax,
 * so the number is parsed rather than matched as a string. What has this carrier got out: by usdot,
 * newest first, because the caller is asking about the one they just took out. What is on the road
 * today: by day, which is the question the enforcement people ask. What is about to expire: which
 * nobody asks and the office needs anyway, because a permit that expires with the load halfway to
 * Presque Isle is a telephone call at four on a Friday.
 *
 * <p>Nothing is ever removed. A void permit stays, an expired permit stays, and a superseded permit
 * stays with its chain intact. The register grows forever and that is the point of it.
 */
public final class PermitRegister {

    /** How many days ahead the expiring list looks unless told otherwise. */
    public static final int EXPIRING_WITHIN = 2;

    private static final Comparator<Permit> BY_NUMBER =
            Comparator.comparing(one -> one.number().toString());

    private final Map<String, Permit> byNumber = new LinkedHashMap<>();
    private final Map<Integer, List<String>> byUsdot = new LinkedHashMap<>();

    /**
     * Put a permit on file.
     *
     * @throws Conflict when a permit with that number is already on file
     */
    public Permit file(Permit permit) {
        String key = permit.number().toString();
        if (byNumber.containsKey(key)) {
            throw new Conflict("permit " + key + " is already on file", Facts.of("number", key));
        }
        byNumber.put(key, permit);
        byUsdot.computeIfAbsent(permit.usdot(), usdot -> new ArrayList<>()).add(key);
        return permit;
    }

    /**
     * A permit by its number as somebody wrote it down.
     *
     * @throws io.github.rkaushikdev.jeepdolly.Unreadable when the text is not a permit number
     * @throws NotOnFile when nobody issued that number
     */
    public Permit get(String number) {
        return get(PermitNumber.parse(number));
    }

    /** @throws NotOnFile when nobody issued that number */
    public Permit get(PermitNumber number) {
        String key = number.toString();
        Permit found = byNumber.get(key);
        if (found == null) {
            throw new NotOnFile("no permit " + key, Facts.of("number", key));
        }
        return found;
    }

    /**
     * Whether a permit with this number is on file.
     *
     * @throws io.github.rkaushikdev.jeepdolly.Unreadable when the text is not a permit number
     */
    public boolean has(String number) {
        return byNumber.containsKey(PermitNumber.parse(number).toString());
    }

    /** A carrier's permits, newest first, because that is the one being asked about. */
    public List<Permit> forCarrier(int usdot) {
        Comparator<Permit> newest = Comparator.comparing(Permit::issued)
                .thenComparing(one -> one.number().toString())
                .reversed();
        return byUsdot.getOrDefault(usdot, List.of()).stream()
                .map(byNumber::get)
                .sorted(newest)
                .toList();
    }

    /** What is good for travel on a day, by number. */
    public List<Permit> onTheRoad(LocalDate day) {
        return byNumber.values().stream()
                .filter(one -> one.validOn(day))
                .sorted(BY_NUMBER)
                .toList();
    }

    /** The day's work: everything issued on one day, by number. */
    public List<Permit> issuedOn(LocalDate day) {
        return byNumber.values().stream()
                .filter(one -> one.issued().equals(day))
                .sorted(BY_NUMBER)
                .toList();
    }

    /** Live permits whose last day falls inside the next two days. */
    public List<Permit> expiring(LocalDate day) {
        return expiring(day, EXPIRING_WITHIN);
    }

    /** Live permits whose last day falls inside the next few days, soonest first. */
    public List<Permit> expiring(LocalDate day, int within) {
        LocalDate horizon = day.plusDays(within);
        return byNumber.values().stream()
                .filter(one -> one.state() == PermitState.ISSUED
                        && !one.end().isBefore(day)
                        && !one.end().isAfter(horizon))
                .sorted(Comparator.comparing(Permit::end).thenComparing(BY_NUMBER))
                .toList();
    }

    public List<Permit> inState(PermitState state) {
        return byNumber.values().stream()
                .filter(one -> one.state() == state)
                .sorted(BY_NUMBER)
                .toList();
    }

    public List<Permit> ofSeries(Series series) {
        return byNumber.values().stream()
                .filter(one -> one.number().series() == series)
                .sorted(BY_NUMBER)
                .toList();
    }

    /** A permit and everything that replaced it, in order, as far as the register holds them. */
    public List<Permit> chain(String number) {
        return chain(PermitNumber.parse(number));
    }

    /** A permit and everything that replaced it, in order, as far as the register holds them. */
    public List<Permit> chain(PermitNumber number) {
        List<Permit> found = new ArrayList<>();
        found.add(get(number));
        Optional<PermitNumber> following = found.get(0).supersededBy();
        while (following.isPresent() && byNumber.containsKey(following.get().toString())) {
            Permit next = get(following.get());
            found.add(next);
            following = next.supersededBy();
        }
        return List.copyOf(found);
    }

    /** Void every live permit a carrier holds, which suspension does, newest first. */
    public List<Permit> voidAllFor(int usdot, LocalDate on, String reason) {
        List<Permit> voided = new ArrayList<>();
        for (Permit permit : forCarrier(usdot)) {
            if (permit.state() == PermitState.ISSUED && !permit.expiredOn(on)) {
                permit.makeVoid(on, reason);
                voided.add(permit);
            }
        }
        return List.copyOf(voided);
    }

    public int size() {
        return byNumber.size();
    }

    /** Whether a permit with exactly this written number is on file, without parsing it. */
    public boolean contains(String number) {
        return byNumber.containsKey(number);
    }

    public boolean contains(PermitNumber number) {
        return byNumber.containsKey(number.toString());
    }
}
