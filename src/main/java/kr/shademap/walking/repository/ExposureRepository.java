package kr.shademap.walking.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class ExposureRepository {
    public record Piece(String exposure, double lengthM, JsonNode geometry) {}
    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    public ExposureRepository(JdbcTemplate jdbc, JsonMapper json) { this.jdbc = jdbc; this.json = json; }

    public boolean network(UUID release, String network) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM shade_api.walking_trial WHERE release_id=? AND network_id=?)", Boolean.class, release, network));
    }

    // Split each directed edge at every native raster grid crossing. Midpoint classification
    // therefore describes the entire piece, without sampling/length approximation or resampling.
    public List<Piece> analyze(UUID release, UUID snapshot, Object geometry) {
        return jdbc.query("""
            WITH raster_data AS MATERIALIZED (
              SELECT r.rast FROM shade_review.shadow_raster r JOIN shade_api.snapshot s
                ON s.dataset_version_id=r.dataset_version_id WHERE s.release_id=? AND s.id=?
            ), input AS (
              SELECT ST_Transform(ST_SetSRID(ST_GeomFromGeoJSON(?),4326),5186) g
            ), edges AS (
              SELECT d.path[1] seq,d.geom g FROM input, LATERAL ST_DumpSegments(g) d
            ), pixel_edges AS (
              SELECT seq,g,ST_Affine(g,1/ST_ScaleX(rast),0,0,1/ST_ScaleY(rast),
                -ST_UpperLeftX(rast)/ST_ScaleX(rast),-ST_UpperLeftY(rast)/ST_ScaleY(rast)) p
              FROM edges,raster_data WHERE ST_Length(g)>0
                AND ST_SRID(rast)=5186 AND ST_SkewX(rast)=0 AND ST_SkewY(rast)=0
            ), boundaries AS (
              SELECT seq,g,0::float8 t FROM pixel_edges UNION ALL SELECT seq,g,1::float8 FROM pixel_edges
              UNION ALL SELECT seq,g,(v-ST_X(ST_StartPoint(p)))/NULLIF(ST_X(ST_EndPoint(p))-ST_X(ST_StartPoint(p)),0)
                FROM pixel_edges,LATERAL generate_series(ceil(least(ST_X(ST_StartPoint(p)),ST_X(ST_EndPoint(p))))::int,
                  floor(greatest(ST_X(ST_StartPoint(p)),ST_X(ST_EndPoint(p))))::int) v
              UNION ALL SELECT seq,g,(v-ST_Y(ST_StartPoint(p)))/NULLIF(ST_Y(ST_EndPoint(p))-ST_Y(ST_StartPoint(p)),0)
                FROM pixel_edges,LATERAL generate_series(ceil(least(ST_Y(ST_StartPoint(p)),ST_Y(ST_EndPoint(p))))::int,
                  floor(greatest(ST_Y(ST_StartPoint(p)),ST_Y(ST_EndPoint(p))))::int) v
            ), cuts AS (
              SELECT seq,g,t,lead(t) OVER(PARTITION BY seq ORDER BY t) next
              FROM (SELECT DISTINCT seq,g,t FROM boundaries WHERE t>=0 AND t<=1) q
            ), pieces AS (
              SELECT seq,t,ST_LineSubstring(g,t,next) g FROM cuts WHERE next-t>1e-12
            ), classified AS (
              SELECT seq,t,g,ST_Value(rast,1,ST_LineInterpolatePoint(g,0.5),true) value FROM pieces,raster_data
            )
            SELECT CASE value WHEN 1 THEN 'SHADE' WHEN 0 THEN 'SUN' ELSE 'UNKNOWN' END exposure,
              ST_Length(g) length,ST_AsGeoJSON(ST_Transform(g,4326),9) geometry
            FROM classified ORDER BY seq,t
            """, (rs,row)->new Piece(rs.getString(1),rs.getDouble(2),json.readTree(rs.getString(3))),
            release, snapshot, json.writeValueAsString(geometry));
    }
}
