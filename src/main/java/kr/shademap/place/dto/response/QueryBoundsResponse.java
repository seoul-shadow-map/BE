package kr.shademap.place.dto.response;

import java.util.List;

public record QueryBoundsResponse(String type, List<List<List<List<Double>>>> coordinates) {}
