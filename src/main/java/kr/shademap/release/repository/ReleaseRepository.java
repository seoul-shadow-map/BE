package kr.shademap.release.repository;

import java.util.List;
import java.util.UUID;
import kr.shademap.release.domain.Release;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReleaseRepository {
    private final JdbcTemplate jdbcTemplate;

    public ReleaseRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Release> findActive() {
        return jdbcTemplate.query(
                "SELECT id, fingerprint FROM shade_api.release WHERE active",
                (rs, rowNumber) -> new Release(rs.getObject("id", UUID.class), rs.getString("fingerprint"))
        );
    }
}
