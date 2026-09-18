package kr.shademap.place.dto.response;

import java.util.UUID;

public record CandidateScopeResponse(
        UUID id,
        String label,
        UUID datasetVersionId,
        String administrativeName,
        String sourceCategory,
        String evidence
) {}
