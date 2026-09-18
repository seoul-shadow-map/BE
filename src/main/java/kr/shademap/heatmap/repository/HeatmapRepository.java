package kr.shademap.heatmap.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.shademap.heatmap.domain.GridCell;
import kr.shademap.heatmap.dto.response.AreaMetrics;
import kr.shademap.place.domain.BoundingBox;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class HeatmapRepository {
    private static final String SELECT = """
            SELECT g.id, ST_AsGeoJSON(ST_Transform(g.geom, 4326), 9) AS geometry,
                   g.target_area_m2, m.valid_area_m2, m.shade_area_m2
            FROM shade_api.heatmap_grid g
            JOIN shade_api.heatmap_metric m ON m.release_id = g.release_id AND m.grid_id = g.id
            WHERE g.release_id = ? AND m.snapshot_id = ?
            """;
    private final JdbcTemplate jdbcTemplate;
    private final JsonMapper jsonMapper;
    private final RowMapper<GridCell> rowMapper;

    public HeatmapRepository(JdbcTemplate jdbcTemplate, JsonMapper jsonMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.jsonMapper = jsonMapper;
        this.rowMapper = (rs, row) -> new GridCell(
                rs.getObject("id", UUID.class), jsonMapper.readTree(rs.getString("geometry")),
                AreaMetrics.of(rs.getDouble("target_area_m2"), rs.getDouble("valid_area_m2"),
                        rs.getDouble("shade_area_m2"))
        );
    }

    public boolean available(UUID releaseId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM shade_api.snapshot WHERE release_id = ?)
                  AND NOT EXISTS (
                    SELECT 1 FROM shade_api.snapshot s WHERE s.release_id = ?
                    AND NOT EXISTS(SELECT 1 FROM shade_api.heatmap_run r
                                   WHERE r.release_id = s.release_id AND r.snapshot_id = s.id)
                  )
                """, Boolean.class, releaseId, releaseId));
    }

    public Optional<JsonNode> policy(UUID releaseId, UUID snapshotId) {
        return jdbcTemplate.query("""
                SELECT policy::text FROM shade_api.heatmap_run WHERE release_id = ? AND snapshot_id = ?
                """, (rs, row) -> jsonMapper.readTree(rs.getString(1)), releaseId, snapshotId).stream().findFirst();
    }

    public List<GridCell> find(UUID releaseId, UUID snapshotId, BoundingBox bounds) {
        return jdbcTemplate.query(SELECT + """
                AND ST_Intersects(g.geom, ST_Transform(ST_MakeEnvelope(?, ?, ?, ?, 4326), 5186))
                ORDER BY g.id LIMIT 5001
                """, rowMapper, releaseId, snapshotId,
                bounds.west(), bounds.south(), bounds.east(), bounds.north());
    }

    public Optional<GridCell> findById(UUID releaseId, UUID snapshotId, UUID gridId) {
        return jdbcTemplate.query(SELECT + " AND g.id = ?", rowMapper,
                releaseId, snapshotId, gridId).stream().findFirst();
    }
}
