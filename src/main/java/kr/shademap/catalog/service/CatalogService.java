package kr.shademap.catalog.service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kr.shademap.heatmap.repository.HeatmapRepository;
import kr.shademap.heatmap.service.HeatmapService;
import kr.shademap.catalog.dto.response.CatalogResponse;
import kr.shademap.catalog.dto.response.CatalogTimeResponse;
import kr.shademap.catalog.dto.response.ConfigResponse;
import kr.shademap.catalog.dto.response.FeatureResponse;
import kr.shademap.catalog.repository.CatalogRepository;
import kr.shademap.global.exception.ApiException;
import kr.shademap.global.response.IssueResponse;
import kr.shademap.place.service.CandidateScopeService;
import kr.shademap.release.service.ReleaseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class CatalogService {
    private final CatalogRepository repository;
    private final ReleaseService releases;
    private final CandidateScopeService scopes;
    private final HeatmapRepository heatmap;

    public CatalogService(CatalogRepository repository, ReleaseService releases, CandidateScopeService scopes, HeatmapRepository heatmap) {
        this.repository = repository;
        this.releases = releases;
        this.scopes = scopes;
        this.heatmap = heatmap;
    }

    public ConfigResponse config(String requestId) {
        var release = releases.active();
        return new ConfigResponse(
                releases.context(release, requestId), null, null, features(release.id()), List.of(), List.of(),
                100, 400, 19, "Asia/Seoul", 5186, repository.findAvailableDates(release.id()),
                repository.findDefaultSnapshotId(release.id()).orElse(null), scopes.findAll(release.id())
        );
    }

    public CatalogResponse catalog(String releaseId, String date, String requestId) {
        var release = releases.requireActive(releaseId);
        LocalDate day;
        try {
            day = LocalDate.parse(date);
        } catch (DateTimeParseException exception) {
            throw ApiException.input("localDate는 YYYY-MM-DD 형식이어야 합니다.");
        }
        var times = repository.findByDate(release.id(), day).stream().map(CatalogTimeResponse::from).toList();
        var issues = times.isEmpty()
                ? List.of(IssueResponse.of("NOT_PROVIDED", "해당 날짜에 제공된 자료가 없습니다."))
                : List.<IssueResponse>of();
        return new CatalogResponse(releases.context(release, requestId), date, times, issues);
    }

    private List<FeatureResponse> features(UUID releaseId) {
        var features = new ArrayList<FeatureResponse>();
        for (int number = 1; number <= 9; number++) {
            String state = number == 1 ? "AVAILABLE"
                    : Set.of(2, 7, 8, 9).contains(number) ? "PARTIAL" : "PREPARING";
            var issues = number == 1 ? List.<IssueResponse>of()
                    : List.of(IssueResponse.of("PREPARING", ReleaseService.LIMITATION));
            features.add(new FeatureResponse("F0" + number, state, issues));
        }
        if (heatmap.available(releaseId)) {
            features.set(2, new FeatureResponse("F03", "PARTIAL",
                    List.of(IssueResponse.of("UNCERTAIN", HeatmapService.LIMITATION))));
        }
        return features;
    }
}
