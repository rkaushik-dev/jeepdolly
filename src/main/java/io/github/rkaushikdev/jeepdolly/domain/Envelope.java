package io.github.rkaushikdev.jeepdolly.domain;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Inches;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The envelope: the box the load sweeps down the road, as the application describes it.
 *
 * <p>Width is the widest point of anything, load or equipment, mirrors excepted. Height is measured
 * off the road surface to the highest point, loaded, and it is the number a hauler most often gets
 * wrong. Length is the whole combination, bumper to the end of the load. The overhangs are what
 * sticks out past the equipment, front and rear, and they are their own question: a sixteen foot
 * rear overhang on a legal length combination still takes out a sign on every corner it turns.
 *
 * <p>An envelope compared to another envelope gives an {@link Excess}. Nothing here decides whether
 * an excess is allowed. That is a table and a route and a season, and it lives elsewhere.
 */
public record Envelope(Inches width, Inches height, Inches length, Inches frontOverhang, Inches rearOverhang) {

    /** The five measures, in the order the application form prints them. */
    public static final List<String> MEASURES = List.of("width", "height", "length", "front_overhang", "rear_overhang");

    public Envelope {
        Objects.requireNonNull(width, "width is a size");
        Objects.requireNonNull(height, "height is a size");
        Objects.requireNonNull(length, "length is a size");
        Objects.requireNonNull(frontOverhang, "front_overhang is a size");
        Objects.requireNonNull(rearOverhang, "rear_overhang is a size");
        if (width.value() <= 0 || height.value() <= 0 || length.value() <= 0) {
            throw new Impossible("a load has a width, a height and a length");
        }
        if (frontOverhang.value() < 0 || rearOverhang.value() < 0) {
            throw new Impossible("an overhang is measured outward or it is nothing");
        }
        if (frontOverhang.plus(rearOverhang).compareTo(length) > 0) {
            throw new Impossible("the overhangs are longer than the whole combination");
        }
    }

    /** A box that projects past its equipment at neither end. */
    public Envelope(Inches width, Inches height, Inches length) {
        this(width, height, length, Inches.ZERO, Inches.ZERO);
    }

    /** A box with only a front overhang. */
    public Envelope(Inches width, Inches height, Inches length, Inches frontOverhang) {
        this(width, height, length, frontOverhang, Inches.ZERO);
    }

    /** Build from what is written on the form, in whatever format, with no overhang. */
    public static Envelope read(String width, String height, String length) {
        return read(width, height, length, "0", "0");
    }

    /** Build from what is written on the form, in whatever format. */
    public static Envelope read(String width, String height, String length, String frontOverhang, String rearOverhang) {
        return new Envelope(
                Inches.parse(width),
                Inches.parse(height),
                Inches.parse(length),
                Inches.parse(frontOverhang),
                Inches.parse(rearOverhang));
    }

    /** Front and rear together, which is what the escort table reads. */
    public Inches overhang() {
        return frontOverhang.plus(rearOverhang);
    }

    public boolean projects() {
        return overhang().value() > 0;
    }

    /** Each measure against the same measure of a limiting envelope. */
    public Excess excessOver(Envelope limit) {
        return new Excess(
                width.over(limit.width),
                height.over(limit.height),
                length.over(limit.length),
                frontOverhang.over(limit.frontOverhang),
                rearOverhang.over(limit.rearOverhang));
    }

    public boolean within(Envelope limit) {
        return !excessOver(limit).any();
    }

    /** One measure by the name the form gives it, such as {@code front_overhang}. */
    public Inches measure(String name) {
        return switch (name) {
            case "width" -> width;
            case "height" -> height;
            case "length" -> length;
            case "front_overhang" -> frontOverhang;
            case "rear_overhang" -> rearOverhang;
            default -> throw new Impossible("an envelope has no " + name);
        };
    }

    /**
     * A copy with some measures made larger, for a what if at the counter.
     *
     * @param by the amount to grow each named measure by, keyed as {@link #MEASURES} names them
     * @throws Impossible when a name is not one of the measures
     */
    public Envelope grown(Map<String, Inches> by) {
        Map<String, Inches> values = new LinkedHashMap<>();
        for (String name : MEASURES) {
            values.put(name, measure(name));
        }
        by.forEach((name, amount) -> {
            if (!values.containsKey(name)) {
                throw new Impossible("an envelope has no " + name);
            }
            values.put(name, values.get(name).plus(amount));
        });
        return new Envelope(
                values.get("width"),
                values.get("height"),
                values.get("length"),
                values.get("front_overhang"),
                values.get("rear_overhang"));
    }

    /** As the permit face prints it: {@code 12 ft wide, 13 ft 6 in high, 80 ft long}. */
    @Override
    public String toString() {
        String base = width + " wide, " + height + " high, " + length + " long";
        if (!projects()) {
            return base;
        }
        return base + ", " + frontOverhang + " front and " + rearOverhang + " rear overhang";
    }
}
