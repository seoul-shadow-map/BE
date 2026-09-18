package kr.shademap.place.controller;

import kr.shademap.place.dto.request.PlaceListRequest;
import kr.shademap.place.dto.response.PlaceDetailResponse;
import kr.shademap.place.dto.response.PlaceListResponse;
import kr.shademap.place.service.PlaceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/{collection:places|stops}")
public class PlaceController {
    private final PlaceService service;

    public PlaceController(PlaceService service) {
        this.service = service;
    }

    @GetMapping
    public PlaceListResponse list(
            @PathVariable String collection,
            @RequestParam String releaseId,
            @RequestParam(required = false) String snapshotId,
            @RequestParam(required = false) String bbox,
            @RequestParam(defaultValue = "MAP") String view,
            @RequestParam(required = false) String candidateScopeId,
            @RequestParam(defaultValue = "false") boolean shadeOnly,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "") String q,
            @RequestAttribute String requestId
    ) {
        var request = new PlaceListRequest(
                collection, releaseId, snapshotId, bbox, view, candidateScopeId, shadeOnly, limit, cursor, q
        );
        return service.list(request, requestId);
    }

    @GetMapping("/{id}")
    public PlaceDetailResponse detail(
            @PathVariable String collection,
            @PathVariable String id,
            @RequestParam String releaseId,
            @RequestParam(required = false) String snapshotId,
            @RequestAttribute String requestId
    ) {
        return service.detail(collection, id, releaseId, snapshotId, requestId);
    }
}
