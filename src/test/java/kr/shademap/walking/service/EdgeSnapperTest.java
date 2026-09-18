package kr.shademap.walking.service;

import java.util.List;
import kr.shademap.walking.domain.WalkingGraph;
import kr.shademap.walking.dto.WalkingRequest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EdgeSnapperTest {
    private WalkingGraph graph() {
        var points = List.of(List.of(0.0,0.0),List.of(50.0,0.0),List.of(50.0,50.0));
        return new WalkingGraph(List.of(new WalkingGraph.Node("a",0,0,0,0),new WalkingGraph.Node("b",50,50,50,50)),
                List.of(new WalkingGraph.Edge("road","a","b",100,points,points)));
    }
    @Test void snapsToInteriorInsteadOfDistantEndpoint() {
        var r=EdgeSnapper.split(graph(),new double[]{20,3},new double[]{50,40},25);
        assertEquals(3,r.origin().distanceM(),1e-8);
        assertEquals(20,r.origin().node().x(),1e-8);
        assertEquals(0,r.origin().node().y(),1e-8);
        assertEquals(3,r.graph().edges().size());
        assertEquals(100,r.graph().edges().stream().mapToDouble(WalkingGraph.Edge::length).sum(),1e-8);
        var middle=r.graph().edges().get(1);
        assertEquals(70,middle.length(),1e-8);
        assertEquals(List.of(List.of(20.0,0.0),List.of(50.0,0.0),List.of(50.0,40.0)),middle.coordinates());
    }
    @Test void sameEdgeReverseAndIdenticalPointsPreserveTopology() {
        var r=EdgeSnapper.split(graph(),new double[]{50,40},new double[]{20,0},25);
        assertEquals("__destination",r.graph().edges().get(1).source());
        assertEquals("__origin",r.graph().edges().get(1).target());
        var same=EdgeSnapper.split(graph(),new double[]{20,0},new double[]{20,0},25);
        assertEquals(same.origin().node().id(),same.destination().node().id());
        assertEquals(2,same.graph().edges().size());
    }
    @Test void endpointsReuseSourceNodeIdsAndOutsideDoesNotConnect() {
        var endpoints=EdgeSnapper.split(graph(),new double[]{0,0},new double[]{50,50},25);
        assertEquals("a",endpoints.origin().node().id());
        assertEquals("b",endpoints.destination().node().id());
        assertEquals(1,endpoints.graph().edges().size());
        assertNull(EdgeSnapper.split(graph(),new double[]{-100,-100},new double[]{50,50},25).origin());
    }
    @Test void invalidCoordinatesAreRejected() {
        assertThrows(RuntimeException.class,()->WalkingService.validate(new WalkingRequest.Point(Double.NaN,37.5)));
        assertThrows(RuntimeException.class,()->WalkingService.validate(new WalkingRequest.Point(37.5,127.0)));
    }
}
