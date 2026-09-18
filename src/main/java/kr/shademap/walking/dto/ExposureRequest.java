package kr.shademap.walking.dto;

public record ExposureRequest(String releaseId, String networkId, String snapshotId,
                              String routeKey, WalkingResponse.Line geometry) {}
