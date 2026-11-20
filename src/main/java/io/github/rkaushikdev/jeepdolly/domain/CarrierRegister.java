package io.github.rkaushikdev.jeepdolly.domain;

import io.github.rkaushikdev.jeepdolly.Conflict;
import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Every carrier the office has ever written a permit for, by usdot number. */
public final class CarrierRegister {

    private final Map<Integer, Carrier> byUsdot = new LinkedHashMap<>();

    /** @throws Conflict when the usdot number is already registered */
    public Carrier add(Carrier carrier) {
        if (byUsdot.containsKey(carrier.usdot())) {
            throw new Conflict(
                    "usdot " + carrier.usdot() + " is already registered", Facts.of("usdot", carrier.usdot()));
        }
        byUsdot.put(carrier.usdot(), carrier);
        return carrier;
    }

    /** @throws NotOnFile when nobody registered that usdot number */
    public Carrier get(int usdot) {
        Carrier found = byUsdot.get(usdot);
        if (found == null) {
            throw new NotOnFile("no carrier with usdot " + usdot, Facts.of("usdot", usdot));
        }
        return found;
    }

    /** Name search, the way the counter does it: any part, any case, sorted by name. */
    public List<Carrier> search(String text) {
        String needle = text.strip().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return List.of();
        }
        return byUsdot.values().stream()
                .filter(one -> one.name().toLowerCase(Locale.ROOT).contains(needle))
                .sorted(Comparator.comparing(Carrier::name))
                .toList();
    }

    /** The carriers suspended on a day, by name, which is what the supervisor reads on Monday. */
    public List<Carrier> suspendedOn(LocalDate day) {
        return byUsdot.values().stream()
                .filter(one -> one.suspensionOn(day).isPresent())
                .sorted(Comparator.comparing(Carrier::name))
                .toList();
    }

    public int size() {
        return byUsdot.size();
    }

    public boolean contains(int usdot) {
        return byUsdot.containsKey(usdot);
    }
}
