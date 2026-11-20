package io.github.rkaushikdev.jeepdolly.support;

import io.github.rkaushikdev.jeepdolly.domain.CommodityBook;
import io.github.rkaushikdev.jeepdolly.domain.Piece;
import io.github.rkaushikdev.jeepdolly.permits.Applicant;
import io.github.rkaushikdev.jeepdolly.permits.Application;
import java.time.LocalDate;
import java.util.List;

/**
 * The application the suite argues about: the excavator move from Waterville to Pittsfield.
 *
 * <p>A test that wants it with one thing different takes {@link #application()}, changes that one
 * field on the builder, and builds it.
 */
public final class Applications {

    /** The reference the excavator move is filed under. */
    public static final String REFERENCE = "25-04471";

    private Applications() {}

    /** The excavator move as filed, ready to have one field changed and be built. */
    public static Application.Builder application() {
        return application(REFERENCE);
    }

    /** The excavator move filed under another reference, ready to have a field changed. */
    public static Application.Builder application(String reference) {
        return Application.builder()
                .reference(reference)
                .usdot(Carriers.BICKFORD)
                .commodity("EXCV")
                .pieces(List.of(new Piece(CommodityBook.standard().get("EXCV"), "Cat 336 excavator", "TMD01297")))
                .combination(Combinations.aCombination())
                .origin("waterville")
                .destination("pittsfield")
                .start(LocalDate.of(2025, 4, 8))
                .end(LocalDate.of(2025, 4, 10))
                .applicant(new Applicant("Dana Bickford", "207 555 0143"))
                .filed(LocalDate.of(2025, 4, 7));
    }

    /** A filed application for the excavator move everybody argues about. */
    public static Application anApplication() {
        return application().build();
    }

    /** The same move filed under another reference. */
    public static Application anApplication(String reference) {
        return application(reference).build();
    }
}
