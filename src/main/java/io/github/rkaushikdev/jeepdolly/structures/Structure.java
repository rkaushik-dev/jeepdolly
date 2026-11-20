package io.github.rkaushikdev.jeepdolly.structures;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.Pounds;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One structure on one segment, as the last inspection left it.
 *
 * <p>Everything else in this system can be argued about. A structure cannot. If a hundred and
 * eighty thousand pounds goes over a 1937 through truss that was rated for less, the bridge is
 * either damaged or it is not, and the office does not get to find out which twice. A structure
 * record is short because the office holds only what it needs to route around one and to know when
 * to send it to an engineer.
 *
 * <p>The spans matter because the bridge formula and the rating both care about length. The
 * ratings come from the last inspection: the inventory rating is what the structure will carry
 * indefinitely, the operating rating what it will carry occasionally, which is exactly what a
 * permit load is. So the operating rating is the number a permit is measured against and the
 * inventory rating is the number a posting is set from.
 *
 * <p>Posting is a decision rather than a measurement. The posted weight is what is on the sign,
 * which is not always what the arithmetic said. Clearance lives here too when the structure goes
 * over a road, so the office has one place to look for the low bridge on US 2 and not two.
 *
 * @param spansFeet the length of each span in whole feet, in order
 * @param built the year it was built, or zero when nobody knows
 */
public record Structure(
        String ident,
        String name,
        String segment,
        StructureKind kind,
        List<Integer> spansFeet,
        int built,
        Optional<LocalDate> inspected,
        Optional<Pounds> inventoryRating,
        Optional<Pounds> operatingRating,
        Optional<Pounds> posted,
        Optional<Inches> clearance,
        Optional<Inches> deckWidth) {

    public Structure {
        spansFeet = List.copyOf(spansFeet);
        if (ident.isBlank()) {
            throw new Impossible("a structure carries an ident");
        }
        if (kind.carriesTraffic()) {
            if (spansFeet.isEmpty()) {
                throw new Impossible(ident + " carries traffic and has spans");
            }
            if (spansFeet.stream().anyMatch(span -> span <= 0)) {
                throw new Impossible(ident + " has a span of no length");
            }
            if (operatingRating.isEmpty()) {
                throw new Impossible(ident + " carries traffic and has an operating rating");
            }
        }
        if (kind.crossesOver() && clearance.isEmpty()) {
            throw new Impossible(ident + " crosses over the road and has a clearance");
        }
        if (inventoryRating.isPresent()
                && operatingRating.isPresent()
                && inventoryRating.get().compareTo(operatingRating.get()) > 0) {
            throw new Impossible(ident + " rates higher for daily traffic than for a permit");
        }
    }

    /** Start a structure record; spans, ratings, posting and clearance are set on the builder. */
    public static Builder builder(String ident, String name, String segment, StructureKind kind) {
        return new Builder(ident, name, segment, kind);
    }

    /** The longest single span in feet, or zero for a structure with none. */
    public int longestSpan() {
        return spansFeet.stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    /** End to end, in feet. */
    public int lengthFeet() {
        return spansFeet.stream().mapToInt(Integer::intValue).sum();
    }

    public int spanCount() {
        return spansFeet.size();
    }

    public boolean isPosted() {
        return posted.isPresent();
    }

    /** How old it is in a year, or zero when nobody knows when it was built. */
    public int age(LocalDate on) {
        return built != 0 ? on.getYear() - built : 0;
    }

    /** Whether the posted sign lets this weight on at all. */
    public boolean carries(Pounds gross) {
        return posted.isEmpty() || gross.compareTo(posted.get()) <= 0;
    }

    /** As the inventory lists it: {@code 1533 Carrabassett Stream on SA148-1, posted 48,000 lb}. */
    @Override
    public String toString() {
        String sign = posted.map(weight -> ", posted " + weight).orElse("");
        return ident + " " + name + " on " + segment + sign;
    }

    /** The optional parts of a structure record, set by name the way the inspection reports them. */
    public static final class Builder {

        private final String ident;
        private final String name;
        private final String segment;
        private final StructureKind kind;
        private final List<Integer> spansFeet = new ArrayList<>();
        private int built;
        private LocalDate inspected;
        private Pounds inventoryRating;
        private Pounds operatingRating;
        private Pounds posted;
        private Inches clearance;
        private Inches deckWidth;

        private Builder(String ident, String name, String segment, StructureKind kind) {
            this.ident = ident;
            this.name = name;
            this.segment = segment;
            this.kind = kind;
        }

        public Builder spansFeet(List<Integer> spans) {
            this.spansFeet.clear();
            this.spansFeet.addAll(spans);
            return this;
        }

        public Builder built(int year) {
            this.built = year;
            return this;
        }

        public Builder inspected(LocalDate day) {
            this.inspected = day;
            return this;
        }

        public Builder inventoryRating(Pounds rating) {
            this.inventoryRating = rating;
            return this;
        }

        public Builder operatingRating(Pounds rating) {
            this.operatingRating = rating;
            return this;
        }

        public Builder posted(Pounds sign) {
            this.posted = sign;
            return this;
        }

        public Builder clearance(Inches height) {
            this.clearance = height;
            return this;
        }

        public Builder deckWidth(Inches width) {
            this.deckWidth = width;
            return this;
        }

        /** @throws Impossible when the record is missing what its kind needs, or rates backwards */
        public Structure build() {
            return new Structure(
                    ident,
                    name,
                    segment,
                    kind,
                    spansFeet,
                    built,
                    Optional.ofNullable(inspected),
                    Optional.ofNullable(inventoryRating),
                    Optional.ofNullable(operatingRating),
                    Optional.ofNullable(posted),
                    Optional.ofNullable(clearance),
                    Optional.ofNullable(deckWidth));
        }
    }
}
