package kr.shademap.heatmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import kr.shademap.heatmap.domain.GridCell;
import kr.shademap.heatmap.dto.response.AreaMetrics;
import org.junit.jupiter.api.Test;

class AreaMetricsTest {
    @Test
    void missingPixelsDoNotDiluteShadeRatio() {
        var metrics = AreaMetrics.of(400, 240, 180);
        assertEquals(0.75, metrics.shadeRatio());
        assertEquals(0.6, metrics.coverage());
        assertEquals(160, metrics.unknownAreaM2());
        assertEquals("PARTIAL_DATA", new GridCell(UUID.randomUUID(), null, metrics).status());
    }

    @Test
    void noDataDoesNotBecomeZeroPercentShade() {
        var metrics = AreaMetrics.of(400, 0, 0);
        assertNull(metrics.shadeRatio());
        assertEquals(0, metrics.coverage());
        assertEquals("NO_DATA", new GridCell(UUID.randomUUID(), null, metrics).status());
    }
}
