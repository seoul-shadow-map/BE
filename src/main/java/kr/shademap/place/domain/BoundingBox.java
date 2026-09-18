package kr.shademap.place.domain;

import java.util.List;
import kr.shademap.global.exception.ApiException;
import kr.shademap.place.dto.response.QueryBoundsResponse;

public record BoundingBox(double west, double south, double east, double north) {
    public static BoundingBox parse(String value) {
        if (value == null || value.length() > 150) {
            throw ApiException.input("지도 범위 bbox가 필요합니다.");
        }
        String[] parts = value.split(",", -1);
        if (parts.length != 4) {
            throw ApiException.input("bbox는 west,south,east,north 형식입니다.");
        }
        double[] coordinates = new double[4];
        try {
            for (int index = 0; index < coordinates.length; index++) {
                coordinates[index] = Double.parseDouble(parts[index]);
            }
        } catch (NumberFormatException exception) {
            throw ApiException.input("bbox 좌표는 숫자여야 합니다.");
        }
        return new BoundingBox(coordinates[0], coordinates[1], coordinates[2], coordinates[3]);
    }

    public BoundingBox {
        for (double coordinate : new double[]{west, south, east, north}) {
            if (!Double.isFinite(coordinate)) {
                throw ApiException.input("bbox 좌표는 유한한 수여야 합니다.");
            }
        }
        if (west < -180 || east > 180 || south < -85 || north > 85 || west >= east || south >= north) {
            throw ApiException.input("bbox 좌표 범위 또는 순서가 잘못됐습니다.");
        }
        double area = (east - west) * 111.32 * Math.cos(Math.toRadians((south + north) / 2))
                * (north - south) * 111.32;
        if (east - west > 1 || north - south > 1 || area > 400) {
            throw ApiException.input("지도를 확대해 400㎢ 이하 범위로 조회해주세요.");
        }
    }

    public QueryBoundsResponse toResponse() {
        var ring = List.of(
                List.of(west, south), List.of(east, south), List.of(east, north),
                List.of(west, north), List.of(west, south)
        );
        return new QueryBoundsResponse("MultiPolygon", List.of(List.of(ring)));
    }
}
