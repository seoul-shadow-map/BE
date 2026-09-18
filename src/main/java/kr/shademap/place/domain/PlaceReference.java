package kr.shademap.place.domain;

import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record PlaceReference(
        UUID id,
        String kind,
        String name,
        String sourceLabel,
        String sourceIdentifier,
        String address,
        JsonNode point,
        UUID datasetVersionId,
        String coordinateRole,
        String geometryStatus,
        UUID canonicalId
) {}
