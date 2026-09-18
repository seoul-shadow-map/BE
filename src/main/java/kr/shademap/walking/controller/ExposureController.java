package kr.shademap.walking.controller;

import java.util.Map;
import kr.shademap.walking.dto.ExposureRequest;
import kr.shademap.walking.service.ExposureService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/walking-exposure")
public class ExposureController {
    private final ExposureService service;
    public ExposureController(ExposureService service) { this.service=service; }
    @PostMapping public Map<String,Object> analyze(@RequestBody ExposureRequest request) { return service.analyze(request); }
}
