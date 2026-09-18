package kr.shademap.release.dto.response;

import java.util.List;
import java.util.UUID;

public record ContextResponse(
        String requestId,
        UUID releaseId,
        UUID policyVersionId,
        UUID networkVersionId,
        String catalogVersion,
        String dataMode,
        String dataStage,
        String generatedAt,
        String analysisMode,
        List<String> limitations
) {}
