package io.github.rkaushikdev.jeepdolly.limits;

import io.github.rkaushikdev.jeepdolly.Impossible;
import io.github.rkaushikdev.jeepdolly.domain.Envelope;
import io.github.rkaushikdev.jeepdolly.domain.Excess;
import io.github.rkaushikdev.jeepdolly.domain.HighwayClass;
import java.util.List;

/**
 * The legal box and the routine box for one class of road.
 *
 * <p>The legal box is what moves with no permit: eight foot six wide, thirteen six high on the state
 * system, seventy five feet of combination. The routine box is what a clerk may issue over the
 * counter. It is generous, because most of the work is a wide excavator or a long beam and there is
 * no sense sending those to an engineer. Past the routine box a person has to look at it, and the
 * office's word for that is a study.
 *
 * <p>Height is the awkward one. Height is not about the load, it is about what the load goes
 * under, and that is a route question. So the routine height is the height above which nothing can
 * be routine on any road, and anything past legal height still gets its clearances walked.
 */
public record SizeTable(HighwayClass highwayClass, Envelope legal, Envelope routine) {

    public SizeTable {
        if (routine.width().compareTo(legal.width()) < 0) {
            throw new Impossible("the routine box is not narrower than the legal one");
        }
        if (routine.height().compareTo(legal.height()) < 0) {
            throw new Impossible("the routine box is not lower than the legal one");
        }
        if (routine.length().compareTo(legal.length()) < 0) {
            throw new Impossible("the routine box is not shorter than the legal one");
        }
    }

    public Excess overLegal(Envelope load) {
        return load.excessOver(legal);
    }

    public Excess overRoutine(Envelope load) {
        return load.excessOver(routine);
    }

    /** Which box the load falls in, read outward from the legal one. */
    public SizeClass classify(Envelope load) {
        if (!overLegal(load).any()) {
            return SizeClass.LEGAL;
        }
        if (!overRoutine(load).any()) {
            return SizeClass.ROUTINE;
        }
        return SizeClass.REVIEW;
    }

    /** Which measures put this load past the counter, in form order. */
    public List<String> reasonsForReview(Envelope load) {
        return overRoutine(load).over();
    }

    @Override
    public String toString() {
        return highwayClass + " size table";
    }
}
