package io.github.rkaushikdev.jeepdolly.persistence.models;

import static io.github.rkaushikdev.jeepdolly.persistence.Columns.CODE_LENGTH;
import static io.github.rkaushikdev.jeepdolly.persistence.Columns.NAME_LENGTH;

import io.github.rkaushikdev.jeepdolly.Miles;
import io.github.rkaushikdev.jeepdolly.domain.Jurisdiction;
import io.github.rkaushikdev.jeepdolly.domain.Route;
import io.github.rkaushikdev.jeepdolly.domain.Segment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Optional;

/**
 * One piece of road between two nodes, with its route designation kept in pieces so a route can be
 * asked for without parsing anything.
 */
@Entity
@Table(name = "segments")
public class SegmentRow {

    @Id
    @Column(name = "ident", length = CODE_LENGTH, nullable = false)
    private String ident;

    @Column(name = "route_kind", length = 4, nullable = false)
    private String routeKind;

    @Column(name = "route_number", nullable = false)
    private int routeNumber;

    @Column(name = "route_suffix", length = 2, nullable = false)
    private String routeSuffix = "";

    @Column(name = "start_node", length = CODE_LENGTH, nullable = false)
    private String startNode;

    @Column(name = "end_node", length = CODE_LENGTH, nullable = false)
    private String endNode;

    @Column(name = "length_hundredths", nullable = false)
    private Miles length;

    @Column(name = "county", length = NAME_LENGTH, nullable = false)
    private String county;

    @Column(name = "town", length = NAME_LENGTH, nullable = false)
    private String town;

    @Column(name = "district", nullable = false)
    private int district;

    @Column(name = "one_way", nullable = false)
    private boolean oneWay;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "start_node", insertable = false, updatable = false)
    private NodeRow start;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "end_node", insertable = false, updatable = false)
    private NodeRow end;

    protected SegmentRow() {}

    /** The row for a segment of the road log. */
    public static SegmentRow of(Segment segment) {
        SegmentRow row = new SegmentRow();
        row.ident = segment.ident();
        row.routeKind = segment.route().kind();
        row.routeNumber = segment.route().number();
        row.routeSuffix = segment.route().suffix();
        row.startNode = segment.start();
        row.endNode = segment.end();
        row.length = segment.length();
        row.county = segment.jurisdiction().county();
        row.town = segment.jurisdiction().town();
        row.district = segment.jurisdiction().district();
        row.oneWay = segment.oneWay();
        return row;
    }

    public String getIdent() {
        return ident;
    }

    public String getRouteKind() {
        return routeKind;
    }

    public int getRouteNumber() {
        return routeNumber;
    }

    public String getRouteSuffix() {
        return routeSuffix;
    }

    public String getStartNode() {
        return startNode;
    }

    public String getEndNode() {
        return endNode;
    }

    public void setEndNode(String endNode) {
        this.endNode = endNode;
    }

    public Miles getLength() {
        return length;
    }

    public void setLength(Miles length) {
        this.length = length;
    }

    public String getCounty() {
        return county;
    }

    public String getTown() {
        return town;
    }

    public int getDistrict() {
        return district;
    }

    public void setDistrict(int district) {
        this.district = district;
    }

    public boolean isOneWay() {
        return oneWay;
    }

    /** The node row this segment starts at, once the row has been read back from the database. */
    public Optional<NodeRow> getStart() {
        return Optional.ofNullable(start);
    }

    /** The node row this segment ends at, once the row has been read back from the database. */
    public Optional<NodeRow> getEnd() {
        return Optional.ofNullable(end);
    }

    public Segment toDomain() {
        return new Segment(
                ident,
                new Route(routeKind, routeNumber, routeSuffix),
                startNode,
                endNode,
                length,
                new Jurisdiction(county, town, district),
                oneWay);
    }

    @Override
    public String toString() {
        return "<SegmentRow " + ident + ">";
    }
}
