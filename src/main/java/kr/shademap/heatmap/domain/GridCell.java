package kr.shademap.heatmap.domain;

import java.util.UUID;
import kr.shademap.heatmap.dto.response.AreaMetrics;
import tools.jackson.databind.JsonNode;

public record GridCell(UUID id, JsonNode geometry, AreaMetrics metrics) {
    public String status() {
        if (metrics.validAreaM2() == 0) return "NO_DATA";
        return metrics.validAreaM2() == metrics.targetAreaM2() ? "READY" : "PARTIAL_DATA";
    }
}
