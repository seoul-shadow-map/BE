package kr.shademap.catalog.controller;

import kr.shademap.catalog.dto.response.CatalogResponse;
import kr.shademap.catalog.dto.response.ConfigResponse;
import kr.shademap.catalog.service.CatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {
    private final CatalogService service;

    public CatalogController(CatalogService service) {
        this.service = service;
    }

    @GetMapping("/config")
    public ConfigResponse config(@RequestAttribute String requestId) {
        return service.config(requestId);
    }

    @GetMapping("/catalog")
    public CatalogResponse catalog(
            @RequestParam String releaseId,
            @RequestParam String localDate,
            @RequestAttribute String requestId
    ) {
        return service.catalog(releaseId, localDate, requestId);
    }
}
