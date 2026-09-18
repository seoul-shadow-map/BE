package kr.shademap.place.dto.response;

import java.util.List;
import kr.shademap.global.response.IssueResponse;
import kr.shademap.release.dto.response.ContextResponse;

public record PlaceListResponse(
        ContextResponse context,
        String status,
        String emptyReason,
        List<PlaceResponse> items,
        String nextCursor,
        QueryBoundsResponse queryBounds,
        List<IssueResponse> issues,
        String view,
        CandidateScopeResponse candidateScope
) {}
