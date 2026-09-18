package kr.shademap.walking.service;

import java.util.*;
import kr.shademap.walking.domain.WalkingGraph;
import kr.shademap.walking.dto.WalkingResponse;

/** Split only the selected source edges; never connect nearby unrelated roads. */
public final class EdgeSnapper {
    private static final double EPSILON = 1e-6;
    public record Result(WalkingGraph graph, WalkingResponse.Snap origin, WalkingResponse.Snap destination) {}
    private record Cut(WalkingGraph.Edge edge, double along, double total, double distance,
                       List<Double> xy, List<Double> lonLat) {}
    private record Break(double along, WalkingGraph.Node node) {}

    public static Result split(WalkingGraph graph, double[] origin, double[] destination, double limit) {
        Cut a = nearest(graph, origin), b = nearest(graph, destination);
        if (a == null || b == null || a.distance() > limit || b.distance() > limit) {
            return new Result(graph, null, null);
        }
        Map<String, WalkingGraph.Node> nodes = new LinkedHashMap<>();
        graph.nodes().forEach(n -> nodes.put(n.id(), n));
        Map<String, List<Break>> cuts = new HashMap<>();
        WalkingGraph.Node start = insert(a, "__origin", nodes, cuts);
        WalkingGraph.Node end = insert(b, "__destination", nodes, cuts);
        List<WalkingGraph.Edge> edges = new ArrayList<>();
        for (var edge : graph.edges()) {
            var breaks = cuts.get(edge.id());
            if (breaks == null) { edges.add(edge); continue; }
            double total = length(edge.projectedCoordinates());
            breaks.add(new Break(0, nodes.get(edge.source())));
            breaks.add(new Break(total, nodes.get(edge.target())));
            breaks.sort(Comparator.comparingDouble(Break::along));
            for (int i = 1; i < breaks.size(); i++) {
                var left = breaks.get(i - 1);
                var right = breaks.get(i);
                if (right.along() - left.along() < EPSILON) continue;
                List<List<Double>> coords = new ArrayList<>();
                coords.add(List.of(left.node().longitude(), left.node().latitude()));
                double along = 0;
                for (int j = 1; j < edge.coordinates().size() - 1; j++) {
                    along += distance(edge.projectedCoordinates().get(j-1), edge.projectedCoordinates().get(j));
                    if (along > left.along() + EPSILON && along < right.along() - EPSILON) coords.add(edge.coordinates().get(j));
                }
                coords.add(List.of(right.node().longitude(), right.node().latitude()));
                // Keep source edge ID for provenance. These fragments exist only for this request.
                edges.add(new WalkingGraph.Edge(edge.id(), left.node().id(), right.node().id(),
                        edge.length() * (right.along()-left.along()) / total, coords));
            }
        }
        return new Result(new WalkingGraph(List.copyOf(nodes.values()), edges),
                new WalkingResponse.Snap(start, a.distance()), new WalkingResponse.Snap(end, b.distance()));
    }

    private static WalkingGraph.Node insert(Cut cut, String id, Map<String, WalkingGraph.Node> nodes,
                                             Map<String, List<Break>> cuts) {
        if (cut.along() < EPSILON) return nodes.get(cut.edge().source());
        if (cut.total()-cut.along() < EPSILON) return nodes.get(cut.edge().target());
        var breaks = cuts.computeIfAbsent(cut.edge().id(), k -> new ArrayList<>());
        for (var existing : breaks) if (Math.abs(existing.along()-cut.along()) < EPSILON) return existing.node();
        var node = new WalkingGraph.Node(id, cut.xy().get(0), cut.xy().get(1), cut.lonLat().get(0), cut.lonLat().get(1));
        nodes.put(id, node);
        breaks.add(new Break(cut.along(), node));
        return node;
    }

    private static Cut nearest(WalkingGraph graph, double[] point) {
        Cut best = null;
        for (var edge : graph.edges()) {
            var xy = edge.projectedCoordinates();
            double total = length(xy), offset = 0;
            if (total <= EPSILON) continue;
            for (int i = 1; i < xy.size(); i++) {
                var a = xy.get(i-1); var b = xy.get(i);
                double dx = b.get(0)-a.get(0), dy = b.get(1)-a.get(1);
                double size = Math.hypot(dx, dy);
                if (size <= EPSILON) continue;
                double t = Math.max(0, Math.min(1, ((point[0]-a.get(0))*dx+(point[1]-a.get(1))*dy)/(size*size)));
                double x = a.get(0)+t*dx, y = a.get(1)+t*dy;
                double distance = Math.hypot(x-point[0], y-point[1]);
                if (best == null || distance < best.distance()-EPSILON
                        || (Math.abs(distance-best.distance()) <= EPSILON && edge.id().compareTo(best.edge().id()) < 0)) {
                    var first = edge.coordinates().get(i-1); var last = edge.coordinates().get(i);
                    var lonLat = List.of(first.get(0)+t*(last.get(0)-first.get(0)), first.get(1)+t*(last.get(1)-first.get(1)));
                    best = new Cut(edge, offset+t*size, total, distance, List.of(x,y), lonLat);
                }
                offset += size;
            }
        }
        return best;
    }

    private static double length(List<List<Double>> coordinates) {
        double result = 0;
        for (int i = 1; i < coordinates.size(); i++) result += distance(coordinates.get(i-1), coordinates.get(i));
        return result;
    }
    private static double distance(List<Double> a, List<Double> b) {
        return Math.hypot(a.get(0)-b.get(0), a.get(1)-b.get(1));
    }
}
