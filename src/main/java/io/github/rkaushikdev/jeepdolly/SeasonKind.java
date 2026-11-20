package io.github.rkaushikdev.jeepdolly;

/**
 * What a seasonal order does to the limit it touches.
 *
 * <p>A thaw order cuts the limit on every posted road in a region for the weeks the road is soft. A
 * frozen order raises it for the deep winter, when the ground is frozen solid four feet down.
 */
public enum SeasonKind implements Valued {
    THAW("thaw"),
    FROZEN("frozen");

    private final String value;

    SeasonKind(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** The kind stored under {@code value}. */
    public static SeasonKind of(String value) {
        return Valued.of(SeasonKind.class, value);
    }

    @Override
    public String toString() {
        return value;
    }
}
