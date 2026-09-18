package kr.shademap.catalog.dto.response;

import java.util.List;
import java.util.UUID;
import kr.shademap.catalog.domain.Snapshot;
import kr.shademap.global.response.IssueResponse;

public record CatalogTimeResponse(
        UUID snapshotId,
        String instant,
        String availability,
        String daylight,
        boolean displayReady,
        boolean analysisReady,
        List<IssueResponse> issues
) {
    public static CatalogTimeResponse from(Snapshot snapshot) {
        return new CatalogTimeResponse(
                snapshot.id(), snapshot.instant().toString(), "AVAILABLE", "UNKNOWN", true, false,
                List.of(IssueResponse.of("POLICY_UNAPPROVED", "표시용 자료이며 장소·경로 분석은 미승인입니다."))
        );
    }
}
