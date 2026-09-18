package kr.shademap.heatmap.controller;

import kr.shademap.heatmap.dto.response.GridResponse;
import kr.shademap.heatmap.dto.response.HeatmapResponse;
import kr.shademap.heatmap.service.HeatmapService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HeatmapController {
    private final HeatmapService service;

    public HeatmapController(HeatmapService service) {
        this.service = service;
    }

    @GetMapping("/heatmap")
    public HeatmapResponse list(
            @RequestParam String releaseId, @RequestParam String snapshotId,
            @RequestParam String bbox, @RequestAttribute String requestId
    ) {
        return service.list(releaseId, snapshotId, bbox, requestId);
    }

    @GetMapping("/grids/{gridId}")
    public GridResponse detail(
            @PathVariable String gridId, @RequestParam String releaseId,
            @RequestParam String snapshotId, @RequestAttribute String requestId
    ) {
        return service.detail(gridId, releaseId, snapshotId, requestId);
    }
}
