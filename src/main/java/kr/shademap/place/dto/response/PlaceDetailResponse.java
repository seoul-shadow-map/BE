package kr.shademap.place.dto.response;

import kr.shademap.release.dto.response.ContextResponse;

public record PlaceDetailResponse(ContextResponse context, PlaceResponse place) {}
