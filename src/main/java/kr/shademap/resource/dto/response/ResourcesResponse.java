package kr.shademap.resource.dto.response;

import java.util.List;
import kr.shademap.global.response.IssueResponse;
import kr.shademap.release.dto.response.ContextResponse;
import tools.jackson.databind.JsonNode;

public record ResourcesResponse(
        ContextResponse context, List<JsonNode> items, List<IssueResponse> issues
) {}
