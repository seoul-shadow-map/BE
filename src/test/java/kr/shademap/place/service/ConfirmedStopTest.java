package kr.shademap.place.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.UUID;
import kr.shademap.catalog.domain.Snapshot;
import kr.shademap.place.domain.PlaceReference;
import kr.shademap.place.dto.response.PlaceResponse;
import org.junit.jupiter.api.Test;

class ConfirmedStopTest {
    @Test
    void confirmedPointDoesNotInventAccessAddressOrDirection() {
        var id = UUID.randomUUID();
        var place = new PlaceReference(id, "STOP", "선릉역", "서울시", "23241", null,
                null, UUID.randomUUID(), "OFFICIAL_STOP_REFERENCE_POINT", "REFERENCE_ONLY", id);
        var snapshot = new Snapshot(UUID.randomUUID(), Instant.parse("2026-09-15T05:00:00Z"));
        for (var state : new String[]{"SHADE", "SUN", "NO_DATA"}) {
            var result = PlaceResponse.confirmedStop(place, snapshot, state);
            assertEquals("VERIFIED", result.verification());
            assertEquals(state, result.observation().state());
            assertEquals(snapshot.id(), result.observation().snapshotId());
            assertEquals("STOP_REFERENCE_POINT", result.observation().unit());
            assertNull(result.accessId());
            assertNull(result.direction());
            assertNull(result.address());
            assertEquals(state.equals("NO_DATA"), !result.observation().issues().isEmpty());
        }
        assertEquals("CANDIDATE", PlaceResponse.from(place, snapshot).verification());
        assertNull(PlaceResponse.confirmedStop(place, null, "NOT_COMPUTED").observation().snapshotId());
    }
}
