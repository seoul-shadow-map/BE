package kr.shademap.place.service;

import java.util.List;
import kr.shademap.catalog.service.SnapshotService;
import kr.shademap.global.exception.ApiException;
import kr.shademap.global.exception.ErrorCode;
import kr.shademap.global.response.IssueResponse;
import kr.shademap.global.validation.Identifiers;
import kr.shademap.place.domain.BoundingBox;
import kr.shademap.place.domain.PlaceReference;
import kr.shademap.place.dto.request.PlaceListRequest;
import kr.shademap.place.dto.response.PlaceDetailResponse;
import kr.shademap.place.dto.response.PlaceListResponse;
import kr.shademap.place.dto.response.PlaceResponse;
import kr.shademap.place.repository.PlaceRepository;
import kr.shademap.release.service.ReleaseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class PlaceService {
    private final PlaceRepository repository;
    private final ReleaseService releases;
    private final SnapshotService snapshots;
    private final CandidateScopeService scopes;

    public PlaceService(
            PlaceRepository repository, ReleaseService releases,
            SnapshotService snapshots, CandidateScopeService scopes
    ) {
        this.repository = repository;
        this.releases = releases;
        this.snapshots = snapshots;
        this.scopes = scopes;
    }

    public PlaceListResponse list(PlaceListRequest request, String requestId) {
        var release = releases.requireActive(request.releaseId());
        var snapshot = snapshots.optional(release.id(), request.snapshotId());
        request.validate();

        boolean mapView = request.view().equals("MAP");
        var bounds = mapView ? BoundingBox.parse(request.bbox()) : null;
        var scope = mapView ? null : scopes.require(release.id(), request.candidateScopeId());
        String binding = request.cursorBinding();
        var after = PlaceCursor.decode(request.cursor(), binding);

        // Unapproved shade analysis must never be inferred from display tiles.
        List<PlaceReference> found = request.shadeOnly() ? List.of() : repository.findPage(
                release.id(), request.kind(), bounds, scope == null ? null : scope.id(),
                after, request.q(), request.limit() + 1
        );
        boolean hasMore = found.size() > request.limit();
        var page = found.subList(0, Math.min(found.size(), request.limit()));
        var confirmed = page.isEmpty() ? java.util.Map.<java.util.UUID, String>of() : repository.confirmedStopStates(release.id(), snapshot == null ? null : snapshot.id(),
                page.stream().map(PlaceReference::id).toList());
        var items = page.stream().map(place -> confirmed.containsKey(place.id())
                ? PlaceResponse.confirmedStop(place, snapshot, confirmed.get(place.id()))
                : PlaceResponse.from(place, snapshot)).toList();
        boolean allConfirmed = !items.isEmpty() && items.stream().allMatch(p -> p.verification().equals("VERIFIED"));
        String nextCursor = hasMore ? PlaceCursor.encode(page.getLast().id(), binding) : null;

        return new PlaceListResponse(
                releases.context(release, requestId), items.isEmpty() ? "EMPTY" : allConfirmed ? "READY" : "CANDIDATE_ONLY",
                emptyReason(request, items.isEmpty()), items, nextCursor,
                bounds == null ? null : bounds.toResponse(),
                allConfirmed ? List.of() : List.of(IssueResponse.of("CANDIDATE_ONLY", ReleaseService.LIMITATION)), request.view(), scope
        );
    }

    public PlaceDetailResponse detail(
            String collection, String id, String releaseId, String snapshotId, String requestId
    ) {
        var release = releases.requireActive(releaseId);
        var snapshot = snapshots.optional(release.id(), snapshotId);
        String kind = collection.equals("stops") ? "STOP" : "REST";
        var place = repository.findById(release.id(), Identifiers.uuid(id), kind)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "해당 장소가 없습니다."));
        var confirmed = repository.confirmedStopStates(release.id(), snapshot == null ? null : snapshot.id(), List.of(place.id()));
        var response = confirmed.containsKey(place.id())
                ? PlaceResponse.confirmedStop(place, snapshot, confirmed.get(place.id()))
                : PlaceResponse.from(place, snapshot);
        return new PlaceDetailResponse(releases.context(release, requestId), response);
    }

    private String emptyReason(PlaceListRequest request, boolean empty) {
        if (!empty) {
            return "NONE";
        }
        if (request.shadeOnly()) {
            return "NO_MATCHING_SHADE";
        }
        return request.view().equals("MAP") ? "NO_PLACES_IN_BOUNDS" : "NO_CANDIDATES_IN_SCOPE";
    }
}
