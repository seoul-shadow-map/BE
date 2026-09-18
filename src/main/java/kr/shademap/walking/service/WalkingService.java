package kr.shademap.walking.service;

import java.util.*;
import kr.shademap.global.exception.ApiException;
import kr.shademap.global.exception.ErrorCode;
import kr.shademap.release.service.ReleaseService;
import kr.shademap.walking.domain.WalkingGraph;
import kr.shademap.walking.dto.WalkingRequest;
import kr.shademap.walking.dto.WalkingResponse;
import kr.shademap.walking.repository.WalkingRepository;
import kr.shademap.walking.repository.PgRoutingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class WalkingService {
    public static final List<String> LIMITATIONS = List.of(
            "로컬 시험용 · 보행 양방향 가정 · 실제 길 안내용으로 승인되지 않았습니다.",
            "2020년 기준 원천 자료이며 현재 통행·출입 제한은 미확인입니다.",
            "선택 위치에서 보행망 선 위의 계산 지점까지의 연결 구간은 경로와 거리에 포함하지 않습니다.",
            "그늘·이동 시간·정류장 대기 및 휴식 위치를 분석하지 않습니다.");
    private final WalkingRepository repository;
    private final ReleaseService releases;
    private final PgRoutingRepository routing;

    public WalkingService(WalkingRepository repository, ReleaseService releases, PgRoutingRepository routing) {
        this.repository = repository;
        this.releases = releases;
        this.routing = routing;
    }

    public JsonNode metadata(String releaseId) {
        return repository.load(releases.requireActive(releaseId).id()).metadata();
    }

    public WalkingResponse route(WalkingRequest request) {
        if (request == null) throw ApiException.input("출발·도착 좌표가 필요합니다.");
        validate(request.origin());
        validate(request.destination());
        var network = repository.load(releases.requireActive(request.releaseId()).id());
        if (!network.id().equals(request.networkId())) throw new ApiException(ErrorCode.VERSION_CHANGED, "보행망 버전이 변경되었습니다. 다시 불러와 주세요.");
        var split = EdgeSnapper.split(network.graph(),
                repository.project(request.origin().longitude(), request.origin().latitude()),
                repository.project(request.destination().longitude(), request.destination().latitude()), 25);
        var origin = split.origin();
        var destination = split.destination();
        if (origin == null || destination == null) return response("OUTSIDE_NETWORK", network.id(), origin, destination, null);
        if (origin.node().id().equals(destination.node().id())) return response("SAME_NODE", network.id(), origin, destination, null);
        var path = routing.shortestPath(split.graph(), origin.node().id(), destination.node().id());
        return response(path.isPresent() ? "READY" : "NO_PATH", network.id(), origin, destination, path.orElse(null));
    }

    public WalkingResponse.Snap snap(WalkingRequest request) {
        if (request == null) throw ApiException.input("선택 좌표가 필요합니다.");
        validate(request.origin());
        var network = repository.load(releases.requireActive(request.releaseId()).id());
        if (!network.id().equals(request.networkId()))
            throw new ApiException(ErrorCode.VERSION_CHANGED, "보행망 버전이 변경되었습니다. 다시 불러와 주세요.");
        var point = repository.project(request.origin().longitude(), request.origin().latitude());
        var result = EdgeSnapper.split(network.graph(), point, point, Double.POSITIVE_INFINITY).origin();
        if (result == null) throw new ApiException(ErrorCode.RESOURCE_UNAVAILABLE, "보행도로를 찾을 수 없습니다.");
        return result;
    }

    static void validate(WalkingRequest.Point point) {
        if (point == null || point.longitude() == null || point.latitude() == null
                || !Double.isFinite(point.longitude()) || !Double.isFinite(point.latitude())
                || point.longitude() < 126 || point.longitude() > 128 || point.latitude() < 37 || point.latitude() > 38) {
            throw ApiException.input("서울 범위의 경도·위도 좌표를 입력해주세요.");
        }
    }

    private WalkingResponse response(String status, String id, WalkingResponse.Snap origin,
                                     WalkingResponse.Snap destination, List<PgRoutingRepository.Step> path) {
        List<List<Double>> coordinates = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        double length = 0;
        if (path != null) for (var step : path) {
            var points = new ArrayList<>(step.edge().coordinates());
            if (step.reverse()) Collections.reverse(points);
            if (!coordinates.isEmpty()) points.removeFirst();
            coordinates.addAll(points);
            ids.add(step.edge().id());
            length += step.edge().length();
        }
        return new WalkingResponse(status, "LOCAL_TRIAL", id, "ASSUMED_BIDIRECTIONAL", "pgRouting/pgr_dijkstra", false, origin, destination,
                path == null ? null : new WalkingResponse.Line("LineString", coordinates), path == null ? null : length, ids, LIMITATIONS);
    }
}
