package kr.shademap.resource.controller;

import kr.shademap.resource.dto.response.ResourcesResponse;
import kr.shademap.resource.service.ResourceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/resources")
public class ResourceController {
    private final ResourceService service;

    public ResourceController(ResourceService service) {
        this.service = service;
    }

    @GetMapping
    public ResourcesResponse resources(
            @RequestParam String releaseId,
            @RequestParam(required = false) String snapshotId,
            @RequestParam(required = false) String kind,
            @RequestAttribute String requestId
    ) {
        return service.resources(releaseId, snapshotId, kind, requestId);
    }
}
