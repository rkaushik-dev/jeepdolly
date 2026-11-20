package io.github.rkaushikdev.jeepdolly.domain;

import io.github.rkaushikdev.jeepdolly.Conflict;
import io.github.rkaushikdev.jeepdolly.Facts;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The commodity table, which the counter reads from and nobody edits live. */
public final class CommodityBook {

    private final Map<String, Commodity> byCode = new LinkedHashMap<>();

    /** The table as it stands, which is the one the tests and the counter use. */
    public static CommodityBook standard() {
        CommodityBook book = new CommodityBook();
        for (Commodity commodity : List.of(
                new Commodity("EXCV", "excavator or shovel", Family.MACHINERY),
                new Commodity("DOZR", "crawler dozer", Family.MACHINERY),
                Commodity.builder("CRNE", "mobile crane", Family.MACHINERY)
                        .highCentre(true)
                        .build(),
                new Commodity("BEAM", "structural steel beam", Family.STRUCTURAL),
                Commodity.builder("GIRD", "bridge girder", Family.STRUCTURAL)
                        .tipLimitDegrees(5)
                        .build(),
                Commodity.builder("TANK", "pressure vessel", Family.VESSEL)
                        .tipLimitDegrees(8)
                        .build(),
                Commodity.builder("BOAT", "boat on a cradle", Family.VESSEL)
                        .highCentre(true)
                        .build(),
                Commodity.builder("MODU", "modular building section", Family.BUILDING)
                        .highCentre(true)
                        .build(),
                Commodity.builder("HOUS", "manufactured home", Family.BUILDING)
                        .highCentre(true)
                        .build(),
                Commodity.builder("XFMR", "power transformer", Family.UTILITY)
                        .tipLimitDegrees(3)
                        .build(),
                new Commodity("POLE", "utility pole", Family.UTILITY),
                Commodity.builder("WOOD", "round wood", Family.FOREST)
                        .divisible(true)
                        .allowance("forest products")
                        .build(),
                Commodity.builder("CHIP", "wood chips", Family.FOREST)
                        .divisible(true)
                        .build(),
                Commodity.builder("HAYB", "baled forage", Family.AGRICULTURAL)
                        .divisible(true)
                        .allowance("forage")
                        .build(),
                Commodity.builder("POTA", "potatoes in bulk", Family.AGRICULTURAL)
                        .divisible(true)
                        .build(),
                Commodity.builder("GRVL", "gravel or aggregate", Family.BULK)
                        .divisible(true)
                        .build(),
                Commodity.builder("LIQD", "liquid in bulk", Family.BULK)
                        .divisible(true)
                        .highCentre(true)
                        .build())) {
            book.add(commodity);
        }
        return book;
    }

    /** @throws Conflict when the code is already on file */
    public Commodity add(Commodity commodity) {
        if (byCode.containsKey(commodity.code())) {
            throw new Conflict("commodity " + commodity.code() + " is already on file");
        }
        byCode.put(commodity.code(), commodity);
        return commodity;
    }

    /** @throws NotOnFile when nobody filed that code */
    public Commodity get(String code) {
        Commodity found = byCode.get(code);
        if (found == null) {
            throw new NotOnFile("no commodity " + code, Facts.of("code", code));
        }
        return found;
    }

    public boolean has(String code) {
        return byCode.containsKey(code);
    }

    /** One family, sorted by code so a report reads the same twice. */
    public List<Commodity> inFamily(Family family) {
        return byCode.values().stream()
                .filter(item -> item.family() == family)
                .sorted(Comparator.comparing(Commodity::code))
                .toList();
    }

    /** The commodities moving under one standing allowance, by code. */
    public List<Commodity> underAllowance(String allowance) {
        return byCode.values().stream()
                .filter(item -> item.allowance().equals(Optional.of(allowance)))
                .sorted(Comparator.comparing(Commodity::code))
                .toList();
    }

    /** Every code on file, sorted. */
    public List<String> codes() {
        return byCode.keySet().stream().sorted().toList();
    }

    public int size() {
        return byCode.size();
    }

    public boolean contains(String code) {
        return byCode.containsKey(code);
    }
}
