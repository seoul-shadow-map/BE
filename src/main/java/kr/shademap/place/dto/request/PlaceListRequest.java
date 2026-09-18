package kr.shademap.place.dto.request;

import java.util.Objects;
import java.util.Set;
import kr.shademap.global.exception.ApiException;

public record PlaceListRequest(
        String collection,
        String releaseId,
        String snapshotId,
        String bbox,
        String view,
        String candidateScopeId,
        boolean shadeOnly,
        int limit,
        String cursor,
        String q
) {
    public void validate() {
        if (limit < 1 || limit > 100) {
            throw ApiException.input("limit은 1~100입니다.");
        }
        if (q.length() > 100) {
            throw ApiException.input("검색어는 100자 이하여야 합니다.");
        }
        if (!Set.of("MAP", "UNLOCATED").contains(view)
                || (collection.equals("stops") && !view.equals("MAP"))) {
            throw ApiException.input("지원하지 않는 목록 범위입니다.");
        }
    }

    public String kind() {
        return collection.equals("stops") ? "STOP" : "REST";
    }

    public String cursorBinding() {
        return String.join("|", releaseId, collection, Objects.toString(snapshotId, ""),
                Objects.toString(bbox, ""), view, Objects.toString(candidateScopeId, ""),
                Boolean.toString(shadeOnly), q, Integer.toString(limit));
    }
}
