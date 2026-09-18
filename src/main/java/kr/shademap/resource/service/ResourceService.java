package kr.shademap.resource.service;

import java.util.List;
import java.util.Set;
import kr.shademap.catalog.service.SnapshotService;
import kr.shademap.global.exception.ApiException;
import kr.shademap.release.service.ReleaseService;
import kr.shademap.resource.dto.response.ResourcesResponse;
import kr.shademap.resource.repository.ResourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ResourceService {
    private static final Set<String> KINDS = Set.of(
            "SHADOW", "HEATMAP", "VALID_MASK", "TERRAIN", "BUILDINGS", "BLOCKS", "POINT_CLOUD"
    );
    private final ResourceRepository repository;
    private final ReleaseService releases;
    private final SnapshotService snapshots;

    public ResourceService(ResourceRepository repository, ReleaseService releases, SnapshotService snapshots) {
        this.repository = repository;
        this.releases = releases;
        this.snapshots = snapshots;
    }

    public ResourcesResponse resources(String releaseId, String snapshotId, String kind, String requestId) {
        var release = releases.requireActive(releaseId);
        var snapshot = snapshots.optional(release.id(), snapshotId);
        if (kind != null && !KINDS.contains(kind)) {
            throw ApiException.input("지원하지 않는 자료 종류입니다.");
        }
        var items = repository.find(release.id(), snapshot == null ? null : snapshot.id(), kind);
        return new ResourcesResponse(releases.context(release, requestId), items, List.of());
    }
}
