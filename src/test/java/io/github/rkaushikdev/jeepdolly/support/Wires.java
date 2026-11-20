package io.github.rkaushikdev.jeepdolly.support;

import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.utilities.Crossing;
import io.github.rkaushikdev.jeepdolly.utilities.LineBook;
import io.github.rkaushikdev.jeepdolly.utilities.LineOwner;

/** The overhead lines of the test network. */
public final class Wires {

    private Wires() {}

    /** Two wires on the test network, one liftable and one on steel towers. */
    public static LineBook aLineBook() {
        LineBook book = new LineBook();
        book.addOwner(new LineOwner("CMP", "Central Maine Power", "207 555 0100", 5));
        book.addOwner(new LineOwner("TEL", "Somerset Telephone", "207 555 0177", 2));
        book.add(new Crossing("L-1", "US201-2", "Fairfield village", Inches.of(19), "CMP", Inches.of(26)));
        book.add(new Crossing("L-2", "I95-2", "Burnham line", Inches.of(24), "CMP"));
        return book;
    }
}
