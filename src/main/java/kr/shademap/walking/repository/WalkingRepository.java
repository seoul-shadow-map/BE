package kr.shademap.walking.repository;

import java.util.UUID;
import kr.shademap.global.exception.ApiException;
import kr.shademap.global.exception.ErrorCode;
import kr.shademap.walking.domain.WalkingGraph;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class WalkingRepository {
    public record Network(String id, WalkingGraph graph, JsonNode metadata) {}
    private final JdbcTemplate jdbc;
    private final JsonMapper mapper;

    public WalkingRepository(JdbcTemplate jdbc, JsonMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public Network load(UUID release) {
        return jdbc.query("""
                SELECT network_id, (graph || jsonb_build_object('edges', (
                    SELECT jsonb_agg(e || jsonb_build_object('projectedCoordinates',
                        (ST_AsGeoJSON(ST_Transform(ST_SetSRID(ST_GeomFromGeoJSON(
                            jsonb_build_object('type','LineString','coordinates',e->'coordinates')::text
                        ),4326),5186),9)::jsonb)->'coordinates'))
                    FROM jsonb_array_elements(graph->'edges') e
                )))::text, metadata::text FROM shade_api.walking_trial WHERE release_id=?
                """,
                (rs, row) -> new Network(rs.getString(1), mapper.readValue(rs.getString(2), WalkingGraph.class), mapper.readTree(rs.getString(3))),
                release).stream().findFirst().orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_UNAVAILABLE, "시험용 보행망을 준비 중입니다."));
    }

    public double[] project(double longitude, double latitude) {
        return jdbc.queryForObject("""
                SELECT ST_X(p), ST_Y(p) FROM
                (SELECT ST_Transform(ST_SetSRID(ST_MakePoint(?,?),4326),5186) p) q
                """, (rs, row) -> new double[]{rs.getDouble(1), rs.getDouble(2)}, longitude, latitude);
    }
}
