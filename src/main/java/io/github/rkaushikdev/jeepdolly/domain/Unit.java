package io.github.rkaushikdev.jeepdolly.domain;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.Pounds;

/**
 * One piece of equipment on the permit face, with a plate and an owner.
 *
 * @param tare the empty weight of the equipment
 * @param unitNumber the fleet number painted on it, or empty
 */
public record Unit(UnitKind kind, int axles, String plate, Pounds tare, String make, String unitNumber) {

    public Unit {
        if (axles < 1) {
            throw new Impossible("a " + kind + " has axles under it");
        }
        if (plate.isBlank()) {
            throw new Impossible("a " + kind + " on a permit carries a plate");
        }
        if (tare.value() < 0) {
            throw new Impossible("equipment does not weigh less than nothing");
        }
    }

    /** A unit with no make or fleet number written down. */
    public Unit(UnitKind kind, int axles, String plate, Pounds tare) {
        this(kind, axles, plate, tare, "", "");
    }

    public Unit withMake(String value) {
        return new Unit(kind, axles, plate, tare, value, unitNumber);
    }

    public Unit withUnitNumber(String value) {
        return new Unit(kind, axles, plate, tare, make, value);
    }

    /** By its fleet number when it has one, its plate when not: {@code tractor 207 (3 axle)}. */
    @Override
    public String toString() {
        String label = unitNumber.isEmpty() ? plate : unitNumber;
        return kind + " " + label + " (" + axles + " axle)";
    }
}
