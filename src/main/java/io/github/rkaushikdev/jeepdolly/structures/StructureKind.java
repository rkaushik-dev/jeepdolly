package io.github.rkaushikdev.jeepdolly.structures;

import io.github.rkaushikdev.jeepdolly.Valued;

/**
 * What sort of structure this is, which decides how it is checked.
 *
 * <p>A bridge or a culvert carries traffic, so a load is rated over it. An overpass or a sign gantry
 * crosses over the road, so it is a clearance question rather than a weight one.
 */
public enum StructureKind implements Valued {
    BRIDGE("bridge"),
    CULVERT("culvert"),
    OVERPASS("overpass"),
    GANTRY("sign gantry");

    private final String value;

    StructureKind(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** The constant stored under {@code value}. */
    public static StructureKind of(String value) {
        return Valued.of(StructureKind.class, value);
    }

    /** Whether a load drives over it and so has to be rated against it. */
    public boolean carriesTraffic() {
        return this == BRIDGE || this == CULVERT;
    }

    /** Whether it spans the road and so has a clearance under it. */
    public boolean crossesOver() {
        return this == OVERPASS || this == GANTRY;
    }

    @Override
    public String toString() {
        return value;
    }
}
