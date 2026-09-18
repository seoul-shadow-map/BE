package kr.shademap.catalog.dto.response;

import java.util.List;
import java.util.UUID;
import kr.shademap.place.dto.response.CandidateScopeResponse;
import kr.shademap.release.dto.response.ContextResponse;

public record ConfigResponse(
        ContextResponse context,
        Object demoBoundary,
        Object routingBoundary,
        List<FeatureResponse> features,
        List<Object> policies,
        List<Object> demoLocations,
        int maxPageSize,
        int maxQueryAreaKm2,
        int maxComparisonTimes,
        String defaultTimeZone,
        int analysisSrid,
        List<String> availableDates,
        UUID defaultSnapshotId,
        List<CandidateScopeResponse> candidateScopes
) {}
