package kr.shademap.walking.dto;

public record WalkingRequest(String releaseId, String networkId, Point origin, Point destination) {
    public record Point(Double longitude, Double latitude) {}
}
