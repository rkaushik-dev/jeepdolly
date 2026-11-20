package io.github.rkaushikdev.jeepdolly.persistence.repositories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.jeepdolly.Inches;
import io.github.rkaushikdev.jeepdolly.NotOnFile;
import io.github.rkaushikdev.jeepdolly.domain.CarrierRegister;
import io.github.rkaushikdev.jeepdolly.domain.CommodityBook;
import io.github.rkaushikdev.jeepdolly.domain.Network;
import io.github.rkaushikdev.jeepdolly.domain.Node;
import io.github.rkaushikdev.jeepdolly.domain.RestrictionKind;
import io.github.rkaushikdev.jeepdolly.domain.Segment;
import io.github.rkaushikdev.jeepdolly.permits.Application;
import io.github.rkaushikdev.jeepdolly.permits.ApplicationState;
import io.github.rkaushikdev.jeepdolly.permits.Permit;
import io.github.rkaushikdev.jeepdolly.permits.PermitNumber;
import io.github.rkaushikdev.jeepdolly.permits.PermitState;
import io.github.rkaushikdev.jeepdolly.permits.Series;
import io.github.rkaushikdev.jeepdolly.persistence.UnitOfWork;
import io.github.rkaushikdev.jeepdolly.persistence.models.ApplicationRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.CarrierRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.CommodityRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.NodeRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.PermitRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.RestrictionRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.SegmentRow;
import io.github.rkaushikdev.jeepdolly.persistence.models.StructureRow;
import io.github.rkaushikdev.jeepdolly.structures.Structure;
import io.github.rkaushikdev.jeepdolly.support.Applications;
import io.github.rkaushikdev.jeepdolly.support.Carriers;
import io.github.rkaushikdev.jeepdolly.support.Databases;
import io.github.rkaushikdev.jeepdolly.support.Permits;
import io.github.rkaushikdev.jeepdolly.support.RoadLog;
import io.github.rkaushikdev.jeepdolly.support.Structures;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The repositories, and the questions each of them is named after. */
class RepositoriesTest {

    private static final CommodityBook BOOK = CommodityBook.standard();
    private static final LocalDate FILED = LocalDate.of(2025, 4, 7);
    private static final LocalDate START = LocalDate.of(2025, 4, 8);

    private UnitOfWork work;
    private EntityManager session;

    @BeforeEach
    void openOnTheReferenceRows() {
        work = Databases.empty().unitOfWork();
        session = work.entityManager();
        for (String code : BOOK.codes()) {
            work.add(CommodityRow.of(BOOK.get(code)));
        }
        CarrierRegister register = Carriers.aCarrierRegister();
        for (int usdot : List.of(Carriers.BICKFORD, Carriers.AROOSTOOK)) {
            work.add(CarrierRow.of(register.get(usdot)));
        }
        Network network = RoadLog.roadLog();
        for (Node node : network.nodes()) {
            work.add(NodeRow.of(node));
        }
        for (Segment segment : network.segments()) {
            work.add(SegmentRow.of(segment));
        }
        work.commit();
        List<String> idents = network.segments().stream().map(Segment::ident).toList();
        for (Structure structure : Structures.structureBook().onSegments(idents)) {
            work.add(StructureRow.of(structure));
        }
        work.commit();
    }

    @AfterEach
    void close() {
        work.close();
    }

    private ApplicationRepository applications() {
        return new ApplicationRepository(session, BOOK);
    }

    private Application aFiledApplication() {
        Application application = Applications.anApplication();
        applications().add(application);
        work.commit();
        return application;
    }

    @Test
    void aCarrierIsFoundByNumberAndByPartOfItsName() {
        CarrierRepository carriers = new CarrierRepository(session);
        assertThat(carriers.get(1204471, START).name()).isEqualTo("Bickford Heavy Haul");
        assertThat(carriers.search("crane")).extracting(CarrierRow::getUsdot).containsExactly(2299001);
        assertThat(carriers.search("  ")).isEmpty();
        assertThat(carriers.count()).isEqualTo(2L);
    }

    @Test
    void aCarrierNobodyRegisteredSaysSo() {
        NotOnFile refusal = catchThrowableOfType(NotOnFile.class, () -> new CarrierRepository(session).row(9999999));
        assertThat(refusal.facts().get("usdot")).isEqualTo(9999999);
    }

    @Test
    void theCommodityBookComesBackWholeAndSkipsTheRetired() {
        assertThat(new CommodityRepository(session).book().size()).isEqualTo(BOOK.size());
        new CommodityRepository(session).retire("CHIP", START);
        work.commit();
        assertThat(new CommodityRepository(session).book(START).size()).isEqualTo(BOOK.size() - 1);
        assertThat(new CommodityRepository(session)
                        .book(LocalDate.of(2025, 1, 1))
                        .size())
                .isEqualTo(BOOK.size());
    }

    @Test
    void retiringSomethingThatIsNotThereSaysSo() {
        assertThatThrownBy(() -> new CommodityRepository(session).retire("ZZZZ", START))
                .isInstanceOf(NotOnFile.class);
    }

    @Test
    void anApplicationIsWrittenAndReadBackThroughTheRepository() {
        aFiledApplication();
        assertThat(applications().get("25-04471").origin()).isEqualTo("waterville");
        assertThat(applications().row("25-04471").getState()).isSameAs(ApplicationState.DRAFT);
    }

    @Test
    void theWaitingQueueIsWhatTheCounterWorksThrough() {
        Application first = Applications.anApplication("25-00001");
        Application second = Applications.anApplication("25-00002");
        first.submit(FILED);
        applications().add(first);
        applications().add(second);
        work.commit();
        assertThat(applications().waiting())
                .extracting(ApplicationRow::getReference)
                .containsExactly("25-00001");
        assertThat(applications().inState(ApplicationState.DRAFT)).hasSize(1);
    }

    @Test
    void anApplicationStateIsSavedBackOntoTheRow() {
        Application application = aFiledApplication();
        application.submit(FILED);
        applications().saveState(application);
        work.commit();
        assertThat(applications().row("25-04471").getState()).isSameAs(ApplicationState.SUBMITTED);
    }

    @Test
    void theApplicationsOfACarrierComeBackNewestFirst() {
        applications()
                .add(Applications.application("25-00001")
                        .filed(LocalDate.of(2025, 4, 1))
                        .build());
        applications().add(Applications.anApplication("25-00002"));
        work.commit();
        assertThat(applications().forCarrier(1204471).get(0).getReference()).isEqualTo("25-00002");
        assertThat(applications().forCarrier(2299001)).isEmpty();
    }

    @Test
    void theApplicationsOfAWeekAreTheWeeksWork() {
        applications()
                .add(Applications.application("25-00001")
                        .filed(LocalDate.of(2025, 4, 1))
                        .build());
        applications().add(Applications.anApplication("25-00002"));
        work.commit();
        assertThat(applications().filedBetween(LocalDate.of(2025, 4, 6), LocalDate.of(2025, 4, 12)))
                .extracting(ApplicationRow::getReference)
                .containsExactly("25-00002");
    }

    @Test
    void aPermitIsWrittenAndReadBackThroughTheRepository() {
        Application application = aFiledApplication();
        PermitRepository permits = new PermitRepository(session);
        permits.add(Permits.aPermit(application));
        work.commit();
        assertThat(permits.get("25-P-004471").carrierName()).isEqualTo("Bickford Heavy Haul");
        assertThatThrownBy(() -> permits.get("25-P-000009")).isInstanceOf(NotOnFile.class);
    }

    @Test
    void whatIsOnTheRoadAndWhatIsExpiringAreDifferentQuestions() {
        Application application = aFiledApplication();
        PermitRepository permits = new PermitRepository(session);
        permits.add(Permits.aPermit(application));
        permits.add(Permits.permit(application)
                .number(new PermitNumber(2025, Series.SINGLE, 4472))
                .end(LocalDate.of(2025, 4, 30))
                .build());
        work.commit();
        assertThat(permits.onTheRoad(START)).hasSize(2);
        assertThat(permits.onTheRoad(LocalDate.of(2025, 4, 20))).hasSize(1);
        assertThat(permits.expiring(START, LocalDate.of(2025, 4, 11)))
                .extracting(PermitRow::getNumber)
                .containsExactly("25-P-004471");
    }

    @Test
    void aVoidedPermitLeavesTheRoadButStaysOnFile() {
        Application application = aFiledApplication();
        PermitRepository permits = new PermitRepository(session);
        Permit permit = Permits.aPermit(application);
        permits.add(permit);
        work.commit();
        permit.makeVoid(START, "load cancelled");
        permits.saveState(permit);
        work.commit();
        assertThat(permits.onTheRoad(START)).isEmpty();
        assertThat(permits.row("25-P-004471").getState()).isSameAs(PermitState.VOID);
        assertThat(permits.row("25-P-004471").getVoidReason()).isEqualTo("load cancelled");
    }

    @Test
    void thePermitsOverASegmentAreWhatAClosureNoticeNeeds() {
        Application application = aFiledApplication();
        PermitRepository permits = new PermitRepository(session);
        permits.add(Permits.aPermit(application));
        work.commit();
        assertThat(permits.touchingSegment("SA148-2")).hasSize(1);
        assertThat(permits.touchingSegment("US2-1")).isEmpty();
    }

    @Test
    void thePermitsIssuedInAWeekAreTheWeeksRevenue() {
        Application application = aFiledApplication();
        PermitRepository permits = new PermitRepository(session);
        permits.add(Permits.aPermit(application));
        work.commit();
        assertThat(permits.issuedBetween(LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 12)))
                .hasSize(1);
        assertThat(permits.issuedBetween(LocalDate.of(2025, 5, 1), LocalDate.of(2025, 5, 12)))
                .isEmpty();
        assertThat(permits.forCarrier(1204471)).hasSize(1);
    }

    @Test
    void theNetworkRepositoryAnswersBySegmentAndByDistrict() {
        NetworkRepository network = new NetworkRepository(session);
        assertThat(network.segment("US201-2").getRouteNumber()).isEqualTo(201);
        assertThat(network.segments()).hasSize(12);
        assertThat(network.inDistrict(4)).hasSize(7);
        assertThatThrownBy(() -> network.segment("NOPE-1")).isInstanceOf(NotOnFile.class);
    }

    @Test
    void theRestrictionsOfADayAreTheOnesInForceThatDay() {
        RestrictionRow clearance = new RestrictionRow("R-1", "US2-1", RestrictionKind.CLEARANCE, "railroad bridge");
        clearance.setHeight(Inches.of(13, 9));
        work.add(clearance);
        RestrictionRow closure = new RestrictionRow("R-2", "US2-1", RestrictionKind.CLOSURE, "culvert");
        closure.setEffective(LocalDate.of(2025, 6, 1));
        closure.setExpires(LocalDate.of(2025, 6, 30));
        work.add(closure);
        work.commit();
        NetworkRepository network = new NetworkRepository(session);
        assertThat(network.restrictionsOn("US2-1", START)).hasSize(1);
        assertThat(network.restrictionsOn("US2-1", LocalDate.of(2025, 6, 15))).hasSize(2);
        assertThat(network.restrictionsOn("SR43-1", START)).isEmpty();
    }

    @Test
    void theStructuresOfASegmentAndThePostedOnesComeOffTheSameTable() {
        NetworkRepository network = new NetworkRepository(session);
        assertThat(network.structuresOn("US201-2"))
                .extracting(StructureRow::getIdent)
                .containsExactly("1247");
        assertThat(network.postedStructures())
                .extracting(StructureRow::getIdent)
                .containsExactly("1533");
    }
}
