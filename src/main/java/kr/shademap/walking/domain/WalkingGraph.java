package kr.shademap.walking.domain;

import java.util.List;

public record WalkingGraph(List<Node> nodes, List<Edge> edges) {
    public record Node(String id, double x, double y, double longitude, double latitude) {}
    public record Edge(String id, String source, String target, double length,
                       List<List<Double>> coordinates, List<List<Double>> projectedCoordinates) {
        public Edge(String id, String source, String target, double length, List<List<Double>> coordinates) {
            this(id, source, target, length, coordinates, null);
        }
    }
}
