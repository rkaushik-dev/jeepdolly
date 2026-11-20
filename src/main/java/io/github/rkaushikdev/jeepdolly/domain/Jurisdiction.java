package io.github.rkaushikdev.jeepdolly.domain;

import io.github.rkaushikdev.jeepdolly.Impossible;

/** Who to ask about a piece of road, and who to bill for damage to it. */
public record Jurisdiction(String county, String town, int district) {

    public Jurisdiction {
        if (district < 1 || district > 9) {
            throw new Impossible("district " + district + " is not one of the state's");
        }
        if (county.isBlank() || town.isBlank()) {
            throw new Impossible("a place is in a county and a town");
        }
    }

    /** The name a seasonal order is written against: {@code district 3}. */
    public String region() {
        return "district " + district;
    }

    @Override
    public String toString() {
        return town + ", " + county + " county (district " + district + ")";
    }
}
