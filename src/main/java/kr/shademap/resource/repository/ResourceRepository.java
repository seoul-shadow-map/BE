package kr.shademap.resource.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class ResourceRepository {
    private final JdbcTemplate jdbcTemplate;
    private final JsonMapper jsonMapper;

    public ResourceRepository(JdbcTemplate jdbcTemplate, JsonMapper jsonMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.jsonMapper = jsonMapper;
    }

    public List<JsonNode> find(UUID releaseId, UUID snapshotId, String kind) {
        // Resource bodies are versioned JSON contracts; retain optional fields as registered.
        return jdbcTemplate.query("""
                SELECT body::text FROM shade_api.resource
                WHERE release_id = ?
                  AND (?::uuid IS NULL OR snapshot_id = ?::uuid OR snapshot_id IS NULL)
                  AND (?::text IS NULL OR kind = ?)
                ORDER BY kind, id
                """, (rs, rowNumber) -> jsonMapper.readTree(rs.getString(1)),
                releaseId, snapshotId, snapshotId, kind, kind);
    }
}
