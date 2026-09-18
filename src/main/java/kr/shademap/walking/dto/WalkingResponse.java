package kr.shademap.walking.dto;

import java.util.List;
import kr.shademap.walking.domain.WalkingGraph;

public record WalkingResponse(String status, String mode, String networkId, String directionPolicy, String routingEngine,
                              boolean navigationApproved, Snap origin, Snap destination,
                              Line geometry, Double lengthM, List<String> edgeIds, List<String> limitations) {
    public record Snap(WalkingGraph.Node node, double distanceM) {}
    public record Line(String type, List<List<Double>> coordinates) {}
}
