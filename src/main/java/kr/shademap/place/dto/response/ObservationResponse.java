package kr.shademap.place.dto.response;

import java.util.List;
import java.util.UUID;
import kr.shademap.catalog.domain.Snapshot;
import kr.shademap.global.response.IssueResponse;

public record ObservationResponse(
        UUID snapshotId,
        String instant,
        String state,
        String unit,
        List<IssueResponse> issues
) {
    public static ObservationResponse notComputed(Snapshot snapshot) {
        return new ObservationResponse(
                snapshot == null ? null : snapshot.id(),
                snapshot == null ? null : snapshot.instant().toString(),
                "NOT_COMPUTED", "CANDIDATE_REFERENCE_POINT",
                List.of(IssueResponse.of("NOT_COMPUTED", "실제 이용 지점이 확정되지 않아 그늘 여부를 제공하지 않습니다."))
        );
    }
}
