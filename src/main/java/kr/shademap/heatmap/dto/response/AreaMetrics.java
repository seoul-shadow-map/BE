package kr.shademap.heatmap.dto.response;

public record AreaMetrics(
        double targetAreaM2, double validAreaM2, double shadeAreaM2,
        double unknownAreaM2, double coverage, Double shadeRatio, String denominator
) {
    public static AreaMetrics of(double target, double valid, double shade) {
        return new AreaMetrics(target, valid, shade, target - valid, valid / target,
                valid == 0 ? null : shade / valid, "VALID_AREA");
    }
}
