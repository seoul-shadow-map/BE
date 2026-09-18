package kr.shademap.place.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import kr.shademap.catalog.service.SnapshotService;
import kr.shademap.place.domain.PlaceReference;
import kr.shademap.place.dto.request.PlaceListRequest;
import kr.shademap.place.repository.PlaceRepository;
import kr.shademap.release.domain.Release;
import kr.shademap.release.service.ReleaseService;
import org.junit.jupiter.api.Test;

class PlaceServiceTest {
    private final PlaceRepository repository = mock(PlaceRepository.class);
    private final ReleaseService releases = mock(ReleaseService.class);
    private final SnapshotService snapshots = mock(SnapshotService.class);
    private final CandidateScopeService scopes = mock(CandidateScopeService.class);
    private final PlaceService service = new PlaceService(repository, releases, snapshots, scopes);
    private final UUID releaseId = UUID.randomUUID();

    @Test
    void shadeOnlyDoesNotQueryOrPresentUnapprovedAnalysis() {
        when(releases.requireActive(releaseId.toString())).thenReturn(new Release(releaseId, "fingerprint"));

        var response = service.list(request(true), "request-id");

        assertEquals("EMPTY", response.status());
        assertEquals("NO_MATCHING_SHADE", response.emptyReason());
        assertEquals(List.of(), response.items());
        verifyNoInteractions(repository);
    }

    @Test
    void extraRowCreatesCursorForLastReturnedPlaceWithoutLeakingExtraRow() {
        when(releases.requireActive(releaseId.toString())).thenReturn(new Release(releaseId, "fingerprint"));
        UUID firstId = UUID.randomUUID();
        when(repository.findPage(eq(releaseId), eq("STOP"), any(), any(), any(), eq(""), anyInt()))
                .thenReturn(List.of(place(firstId), place(UUID.randomUUID())));
        var request = request(false);

        var response = service.list(request, "request-id");

        assertEquals(1, response.items().size());
        assertEquals(firstId, PlaceCursor.decode(response.nextCursor(), request.cursorBinding()));
        assertEquals("NOT_COMPUTED", response.items().getFirst().observation().state());
        assertNull(response.items().getFirst().observation().snapshotId());
        assertEquals("CANDIDATE", response.items().getFirst().verification());
    }

    private PlaceListRequest request(boolean shadeOnly) {
        return new PlaceListRequest("stops", releaseId.toString(), null, "127.04,37.50,127.07,37.52",
                "MAP", null, shadeOnly, 1, null, "");
    }

    private PlaceReference place(UUID id) {
        return new PlaceReference(id, "STOP", "정류장", "source", "12345", null, null,
                UUID.randomUUID(), "OFFICIAL_STOP_REFERENCE_POINT", "REFERENCE_ONLY", id);
    }
}
