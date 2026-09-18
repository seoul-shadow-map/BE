package kr.shademap.heatmap.service;

import java.util.List;
import java.util.UUID;
import kr.shademap.catalog.service.SnapshotService;
import kr.shademap.global.exception.ApiException;
import kr.shademap.global.exception.ErrorCode;
import kr.shademap.global.response.IssueResponse;
import kr.shademap.global.validation.Identifiers;
import kr.shademap.heatmap.dto.response.GridResponse;
import kr.shademap.heatmap.dto.response.HeatmapResponse;
import kr.shademap.heatmap.repository.HeatmapRepository;
import kr.shademap.place.domain.BoundingBox;
import kr.shademap.release.service.ReleaseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class HeatmapService {
    public static final String LIMITATION =
            "건물·수목 포함 DSM 그림자의 평면 면적 비율입니다. 보행 가능 지면·실제 이용 지점의 그늘 비율이 아닙니다.";
    private final HeatmapRepository repository;
    private final ReleaseService releases;
    private final SnapshotService snapshots;

    public HeatmapService(HeatmapRepository repository, ReleaseService releases, SnapshotService snapshots) {
        this.repository = repository;
        this.releases = releases;
        this.snapshots = snapshots;
    }

    public HeatmapResponse list(String releaseId, String snapshotId, String bbox, String requestId) {
        var release = releases.requireActive(releaseId);
        Identifiers.uuid(snapshotId);
        var snapshot = snapshots.optional(release.id(), snapshotId);
        var policy = requirePolicy(release.id(), snapshot.id());
        var cells = repository.find(release.id(), snapshot.id(), BoundingBox.parse(bbox));
        if (cells.size() > 5000) throw ApiException.input("지도를 확대해 더 작은 영역을 조회해주세요.");
        return new HeatmapResponse(releases.context(release, requestId), snapshot.id(),
                snapshot.instant().toString(), policy, "FeatureCollection",
                cells.stream().map(HeatmapResponse.GridFeature::from).toList(),
                List.of(IssueResponse.of("UNCERTAIN", LIMITATION)));
    }

    public GridResponse detail(String gridId, String releaseId, String snapshotId, String requestId) {
        var release = releases.requireActive(releaseId);
        Identifiers.uuid(snapshotId);
        var snapshot = snapshots.optional(release.id(), snapshotId);
        var policy = requirePolicy(release.id(), snapshot.id());
        var cell = repository.findById(release.id(), snapshot.id(), Identifiers.uuid(gridId))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "해당 격자가 없습니다."));
        return new GridResponse(releases.context(release, requestId), cell.id(), snapshot.id(),
                snapshot.instant().toString(), cell.status(), cell.geometry(), cell.metrics(),
                policy.get("gridSizeM").asDouble(), List.of(IssueResponse.of("UNCERTAIN", LIMITATION)));
    }

    private JsonNode requirePolicy(UUID releaseId, UUID snapshotId) {
        return repository.policy(releaseId, snapshotId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_UNAVAILABLE, "해당 시각의 히트맵 집계를 준비 중입니다."));
    }
}
