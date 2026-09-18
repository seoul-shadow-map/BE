package kr.shademap.place.dto.response;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import kr.shademap.catalog.domain.Snapshot;
import kr.shademap.global.response.IssueResponse;
import kr.shademap.place.domain.PlaceReference;
import kr.shademap.release.service.ReleaseService;
import tools.jackson.databind.JsonNode;

public record PlaceResponse(
        UUID id,
        String kind,
        String name,
        String verification,
        String sourceLabel,
        String sourceIdentifier,
        String direction,
        String address,
        JsonNode point,
        ObservationResponse observation,
        UUID accessId,
        List<String> facilities,
        List<String> limitations,
        List<IssueResponse> issues,
        UUID candidateScopeId,
        String coordinateRole,
        String coordinateStatus,
        UUID canonicalId,
        UUID sourceVersionId
) {
    public static PlaceResponse confirmedStop(PlaceReference place, Snapshot snapshot, String state) {
        var issues = state.equals("SHADE") || state.equals("SUN") ? List.<IssueResponse>of()
                : List.of(IssueResponse.of(state, snapshot == null
                        ? "시각을 선택하면 그늘 상태를 확인할 수 있습니다."
                        : "해당 위치의 유효한 그림자 자료가 없습니다."));
        var observation = new ObservationResponse(
                snapshot == null ? null : snapshot.id(),
                snapshot == null ? null : snapshot.instant().toString(),
                state, "STOP_REFERENCE_POINT", issues);
        return new PlaceResponse(
                place.id(), place.kind(), place.name(), "VERIFIED", place.sourceLabel(),
                place.sourceIdentifier(), null, place.address(), place.point(), observation,
                null, List.of(), List.of("사용자 위치 확인 완료 · 건물·수목 포함 DSM 기준 정류장 지점 판정"),
                issues, null, place.coordinateRole(), place.geometryStatus(), place.canonicalId(),
                place.datasetVersionId());
    }

    public static PlaceResponse from(PlaceReference place, Snapshot snapshot) {
        String role = Objects.toString(place.coordinateRole(), "");
        return new PlaceResponse(
                place.id(), place.kind(), Objects.toString(place.name(), "이름 미확인"), "CANDIDATE",
                Objects.toString(place.sourceLabel(), "원본 검수 자료"), place.sourceIdentifier(), null,
                place.address(), place.point(), ObservationResponse.notComputed(snapshot), null, List.of(),
                List.of(ReleaseService.LIMITATION, roleLabel(role)),
                List.of(IssueResponse.of("CANDIDATE_ONLY", "위치·이용 조건 검수 중")),
                place.datasetVersionId(), role, place.geometryStatus(), place.canonicalId(), place.datasetVersionId()
        );
    }

    private static String roleLabel(String role) {
        return switch (role) {
            case "OFFICIAL_STOP_REFERENCE_POINT" -> "정류장 대표 위치 · 실제 대기면 미확정";
            case "PARK_CENTRAL_POINT", "LINKED_PARK_CENTRAL_POINT" -> "공원 중앙 위치 · 출입구와 다를 수 있음";
            case "OFFICIAL_PARCEL_REFERENCE_POINT" -> "필지 대표 위치 · 실제 휴식 지점 미확정";
            default -> "위치 역할 미확정";
        };
    }
}
