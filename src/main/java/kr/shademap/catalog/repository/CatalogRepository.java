package kr.shademap.catalog.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.shademap.catalog.domain.Snapshot;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogRepository {
    private static final RowMapper<Snapshot> SNAPSHOT_MAPPER = (rs, rowNumber) ->
            new Snapshot(rs.getObject("id", UUID.class), rs.getTimestamp("instant").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public CatalogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Snapshot> findSnapshot(UUID releaseId, UUID snapshotId) {
        return jdbcTemplate.query("""
                SELECT id, instant FROM shade_api.snapshot
                WHERE release_id = ? AND id = ?
                """, SNAPSHOT_MAPPER, releaseId, snapshotId).stream().findFirst();
    }

    public List<Snapshot> findByDate(UUID releaseId, LocalDate date) {
        return jdbcTemplate.query("""
                SELECT id, instant FROM shade_api.snapshot
                WHERE release_id = ? AND local_date = ? ORDER BY instant
                """, SNAPSHOT_MAPPER, releaseId, date);
    }

    public List<String> findAvailableDates(UUID releaseId) {
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT local_date::text FROM shade_api.snapshot
                WHERE release_id = ? ORDER BY local_date::text
                """, String.class, releaseId);
    }

    public Optional<UUID> findDefaultSnapshotId(UUID releaseId) {
        return jdbcTemplate.queryForList("""
                SELECT id FROM shade_api.snapshot WHERE release_id = ?
                ORDER BY CASE WHEN (instant AT TIME ZONE 'Asia/Seoul')::time = '10:00'
                              THEN 0 ELSE 1 END, instant
                LIMIT 1
                """, UUID.class, releaseId).stream().findFirst();
    }
}
