package kr.shademap.walking.controller;

import kr.shademap.walking.dto.WalkingRequest;
import kr.shademap.walking.dto.WalkingResponse;
import kr.shademap.walking.service.WalkingService;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1")
public class WalkingController {
    private final WalkingService service;

    public WalkingController(WalkingService service) { this.service = service; }

    @GetMapping("/walking-network")
    public JsonNode network(@RequestParam String releaseId) { return service.metadata(releaseId); }

    @PostMapping("/walking-routes")
    public WalkingResponse route(@RequestBody WalkingRequest request) { return service.route(request); }

    @PostMapping("/walking-snap")
    public WalkingResponse.Snap snap(@RequestBody WalkingRequest request) { return service.snap(request); }
}
