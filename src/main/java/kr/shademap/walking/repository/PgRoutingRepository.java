package kr.shademap.walking.repository;

import java.util.*;
import kr.shademap.walking.domain.WalkingGraph;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PgRoutingRepository {
    public record Step(WalkingGraph.Edge edge, boolean reverse) {}
    private final JdbcTemplate jdbc;

    public PgRoutingRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Request-local split edges: only generated integer IDs and finite costs enter SQL. */
    public Optional<List<Step>> shortestPath(WalkingGraph graph, String origin, String destination) {
        Map<String, Integer> nodes = new HashMap<>();
        for (var node : graph.nodes()) nodes.put(node.id(), nodes.size()+1);
        if (!nodes.containsKey(origin) || !nodes.containsKey(destination) || graph.edges().isEmpty()) return Optional.empty();
        StringJoiner values = new StringJoiner(",");
        for (int i = 0; i < graph.edges().size(); i++) {
            var edge = graph.edges().get(i);
            if (!nodes.containsKey(edge.source()) || !nodes.containsKey(edge.target())
                    || !Double.isFinite(edge.length()) || edge.length() <= 0) throw new IllegalArgumentException("Invalid graph");
            values.add("("+(i+1)+","+nodes.get(edge.source())+","+nodes.get(edge.target())+","+Double.toString(edge.length())+"::float8)");
        }
        // No user-provided identifiers, coordinate strings, or SQL fragments are interpolated.
        String edgesSql = "SELECT id::bigint, source::bigint, target::bigint, cost FROM (VALUES "
                + values + ") AS e(id,source,target,cost)";
        var steps = jdbc.query("""
                SELECT node, edge FROM pgr_dijkstra(?::text, ?::bigint, ?::bigint, false)
                WHERE edge <> -1 ORDER BY path_seq
                """, (rs, row) -> {
                    var edge = graph.edges().get(rs.getInt("edge")-1);
                    return new Step(edge, rs.getInt("node") != nodes.get(edge.source()));
                }, edgesSql, nodes.get(origin), nodes.get(destination));
        return steps.isEmpty() ? Optional.empty() : Optional.of(steps);
    }
}
