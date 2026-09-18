package kr.shademap.catalog.dto.response;

import java.util.List;
import kr.shademap.global.response.IssueResponse;

public record FeatureResponse(String featureId, String state, List<IssueResponse> issues) {}
