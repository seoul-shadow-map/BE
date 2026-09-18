package kr.shademap.heatmap.dto.response;

import java.util.List;
import java.util.UUID;
import kr.shademap.global.response.IssueResponse;
import kr.shademap.heatmap.domain.GridCell;
import kr.shademap.release.dto.response.ContextResponse;
import tools.jackson.databind.JsonNode;

public record HeatmapResponse(
        ContextResponse context, UUID snapshotId, String instant, JsonNode policy,
        String type, List<GridFeature> features, List<IssueResponse> issues
) {
    public record GridProperties(UUID areaId, String status, AreaMetrics metrics) {}

    public record GridFeature(String type, UUID id, JsonNode geometry, GridProperties properties) {
        public static GridFeature from(GridCell cell) {
            return new GridFeature("Feature", cell.id(), cell.geometry(),
                    new GridProperties(cell.id(), cell.status(), cell.metrics()));
        }
    }
}
