package kr.shademap.place;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import kr.shademap.global.exception.ApiException;
import kr.shademap.global.validation.Identifiers;
import kr.shademap.place.domain.BoundingBox;
import kr.shademap.place.service.PlaceCursor;
import org.junit.jupiter.api.Test;

class PlaceQueryValidationTest {
    @Test
    void rejectsInvalidAndUnboundedQueries() {
        String[] invalidBounds = {
                "NaN,37,128,38", "127,38,126,37", "-180,-85,180,85",
                "127,37,127,38", "127,37,128", "127,37,Infinity,38"
        };
        for (String bounds : invalidBounds) {
            assertThrows(ApiException.class, () -> BoundingBox.parse(bounds));
        }
        assertEquals(new BoundingBox(127.04, 37.50, 127.07, 37.52),
                BoundingBox.parse("127.04,37.50,127.07,37.52"));
    }

    @Test
    void cursorCannotSilentlyCrossQueryOrRelease() {
        UUID id = UUID.randomUUID();
        String encoded = PlaceCursor.encode(id, "release-A|stops|bounds-A");

        assertEquals(id, PlaceCursor.decode(encoded, "release-A|stops|bounds-A"));
        assertThrows(ApiException.class, () -> PlaceCursor.decode(encoded, "release-B|stops|bounds-A"));
        assertThrows(ApiException.class, () -> PlaceCursor.decode(encoded, "release-A|places|bounds-B"));
        assertThrows(ApiException.class, () -> PlaceCursor.decode("not-a-cursor", "same"));
    }

    @Test
    void rejectsTruncatedAndInjectedIdentifiers() {
        assertThrows(ApiException.class, () -> Identifiers.uuid("1-1-1-1-1"));
        assertThrows(ApiException.class, () -> Identifiers.uuid("' OR 1=1 --"));
    }
}
