package kr.shademap.place.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kr.shademap.place.domain.BoundingBox;
import kr.shademap.place.domain.PlaceReference;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class PlaceRepository {
    private static final String SELECT = """
            SELECT p.id, p.kind, p.name, p.source_label, p.source_identifier, p.address,
                   p.dataset_version_id, p.coordinate_role, p.geometry_status, p.canonical_id,
                   ST_AsGeoJSON(ST_Transform(p.geom, 4326), 9) AS point_json
            FROM shade_api.place_reference p
            """;
    private static final String LIST_FILTER = """
            WHERE p.release_id = :release AND p.kind = :kind AND p.id = p.canonical_id
              AND p.id > :after
              AND (:q = '' OR position(lower(:q) in lower(
                  coalesce(p.name, '') || ' ' || coalesce(p.source_identifier, '') || ' ' ||
                  coalesce(p.address, '')
              )) > 0)
            """;
    private static final String MAP_FILTER = """
            AND p.geom && ST_Transform(ST_MakeEnvelope(:w, :s, :e, :n, 4326), 5186)
            AND ST_Intersects(ST_Transform(p.geom, 4326), ST_MakeEnvelope(:w, :s, :e, :n, 4326))
            """;
    private static final String SCOPE_FILTER = """
            AND p.geom IS NULL AND p.dataset_version_id = :scope
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final RowMapper<PlaceReference> rowMapper;

    public PlaceRepository(NamedParameterJdbcTemplate jdbcTemplate, JsonMapper jsonMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.rowMapper = (rs, rowNumber) -> {
            String geometry = rs.getString("point_json");
            return new PlaceReference(
                    rs.getObject("id", UUID.class), rs.getString("kind"), rs.getString("name"),
                    rs.getString("source_label"), rs.getString("source_identifier"), rs.getString("address"),
                    geometry == null ? null : jsonMapper.readTree(geometry),
                    rs.getObject("dataset_version_id", UUID.class), rs.getString("coordinate_role"),
                    rs.getString("geometry_status"), rs.getObject("canonical_id", UUID.class)
            );
        };
    }

    public List<PlaceReference> findPage(
            UUID releaseId, String kind, BoundingBox bounds, UUID scopeId,
            UUID after, String query, int fetchSize
    ) {
        var parameters = new MapSqlParameterSource()
                .addValue("release", releaseId).addValue("kind", kind)
                .addValue("after", after).addValue("q", query).addValue("limit", fetchSize);
        String spatialFilter;
        if (bounds != null) {
            // Native CRS index narrows candidates before the exact API-CRS intersection.
            spatialFilter = MAP_FILTER;
            parameters.addValue("w", bounds.west()).addValue("s", bounds.south())
                    .addValue("e", bounds.east()).addValue("n", bounds.north());
        } else {
            spatialFilter = SCOPE_FILTER;
            parameters.addValue("scope", scopeId);
        }
        return jdbcTemplate.query(
                SELECT + LIST_FILTER + spatialFilter + " ORDER BY p.id LIMIT :limit",
                parameters, rowMapper
        );
    }

    public Optional<PlaceReference> findById(UUID releaseId, UUID id, String kind) {
        return jdbcTemplate.query(
                SELECT + " WHERE p.release_id = :release AND p.id = :id AND p.kind = :kind",
                Map.of("release", releaseId, "id", id, "kind", kind), rowMapper
        ).stream().findFirst();
    }

    public Map<UUID, String> confirmedStopStates(UUID releaseId, UUID snapshotId, List<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        var parameters = new MapSqlParameterSource()
                .addValue("release", releaseId).addValue("snapshot", snapshotId)
                .addValue("ids", ids);
        var states = new java.util.HashMap<UUID, String>();
        jdbcTemplate.query("""
                SELECT c.place_id, coalesce(o.state, 'NOT_COMPUTED') AS state
                FROM shade_api.stop_confirmation c
                JOIN shade_api.place_reference p ON p.release_id=c.release_id AND p.id=c.place_id
                    AND ST_Equals(p.geom,c.geom)
                LEFT JOIN shade_api.stop_observation o ON o.release_id=c.release_id
                    AND o.place_id=c.place_id AND o.snapshot_id=:snapshot
                WHERE c.release_id=:release AND c.place_id IN (:ids)
                """, parameters, (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                states.put(rs.getObject("place_id", UUID.class), rs.getString("state")));
        return states;
    }
}
