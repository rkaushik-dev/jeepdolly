package io.github.rkaushikdev.jeepdolly.persistence.models;

import static io.github.rkaushikdev.jeepdolly.persistence.Columns.CODE_LENGTH;
import static io.github.rkaushikdev.jeepdolly.persistence.Columns.NAME_LENGTH;

import io.github.rkaushikdev.jeepdolly.domain.Node;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A place a route can turn, start or end, on the road log.
 *
 * <p>The road log changes twice a year and the restrictions change every week, so they are two
 * tables joined by segment ident rather than one table with a lot of nullable columns.
 */
@Entity
@Table(name = "nodes")
public class NodeRow {

    @Id
    @Column(name = "name", length = CODE_LENGTH, nullable = false)
    private String name;

    @Column(name = "town", length = NAME_LENGTH, nullable = false)
    private String town;

    protected NodeRow() {}

    public NodeRow(String name, String town) {
        this.name = name;
        this.town = town;
    }

    public static NodeRow of(Node node) {
        return new NodeRow(node.name(), node.town());
    }

    public String getName() {
        return name;
    }

    public String getTown() {
        return town;
    }

    public void setTown(String town) {
        this.town = town;
    }

    public Node toDomain() {
        return new Node(name, town);
    }

    @Override
    public String toString() {
        return "<NodeRow " + name + ">";
    }
}
