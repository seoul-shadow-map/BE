package kr.shademap.heatmap.dto.response;

import java.util.List;
import java.util.UUID;
import kr.shademap.global.response.IssueResponse;
import kr.shademap.release.dto.response.ContextResponse;
import tools.jackson.databind.JsonNode;

public record GridResponse(
        ContextResponse context, UUID areaId, UUID snapshotId, String instant,
        String status, JsonNode geometry, AreaMetrics metrics, double gridSizeM,
        List<IssueResponse> issues
) {}
