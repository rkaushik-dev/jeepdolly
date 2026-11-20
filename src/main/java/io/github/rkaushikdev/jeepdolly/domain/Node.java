package io.github.rkaushikdev.jeepdolly.domain;

import io.github.rkaushikdev.jeepdolly.Impossible;

/**
 * A place a route can turn, start, or end: an intersection, a town line, a bridge worth naming.
 *
 * <p>Nodes are named rather than numbered where anybody would name them, because a permit that says
 * turn at Skowhegan is a permit a driver can follow and a permit that says turn at node 41127 is
 * not.
 */
public record Node(String name, String town) {

    public Node {
        if (name.isBlank()) {
            throw new Impossible("a node is named");
        }
    }

    @Override
    public String toString() {
        return name;
    }
}
