package kr.shademap.catalog.dto.response;

import java.util.List;
import kr.shademap.global.response.IssueResponse;
import kr.shademap.release.dto.response.ContextResponse;

public record CatalogResponse(
        ContextResponse context,
        String localDate,
        List<CatalogTimeResponse> times,
        List<IssueResponse> issues
) {}
