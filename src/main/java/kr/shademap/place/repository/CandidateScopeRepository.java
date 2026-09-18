package kr.shademap.place.repository;

import java.util.List;
import java.util.UUID;
import kr.shademap.place.dto.response.CandidateScopeResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CandidateScopeRepository {
    private final JdbcTemplate jdbcTemplate;

    public CandidateScopeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<CandidateScopeResponse> findByRelease(UUID releaseId) {
        return jdbcTemplate.query("""
                SELECT dataset_version_id, min(source_label) AS source_label
                FROM shade_api.place_reference
                WHERE release_id = ? AND kind = 'REST' AND geom IS NULL
                GROUP BY dataset_version_id ORDER BY dataset_version_id
                """, (rs, rowNumber) -> new CandidateScopeResponse(
                        rs.getObject("dataset_version_id", UUID.class),
                        rs.getString("source_label") + " · 위치 확인 중",
                        rs.getObject("dataset_version_id", UUID.class), null, "REST",
                        "적재 원본 버전별 위치 미확정 자료"
                ), releaseId);
    }
}
