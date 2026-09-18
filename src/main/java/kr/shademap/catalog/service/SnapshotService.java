package kr.shademap.catalog.service;

import java.util.UUID;
import kr.shademap.catalog.domain.Snapshot;
import kr.shademap.catalog.repository.CatalogRepository;
import kr.shademap.global.exception.ApiException;
import kr.shademap.global.exception.ErrorCode;
import kr.shademap.global.validation.Identifiers;
import org.springframework.stereotype.Service;

@Service
public class SnapshotService {
    private final CatalogRepository repository;

    public SnapshotService(CatalogRepository repository) {
        this.repository = repository;
    }

    public Snapshot optional(UUID releaseId, String snapshotId) {
        if (snapshotId == null) {
            return null;
        }
        return repository.findSnapshot(releaseId, Identifiers.uuid(snapshotId))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "해당 시각 자료가 없습니다."));
    }
}
