package kr.shademap.release.service;

import java.time.Instant;
import java.util.List;
import kr.shademap.global.exception.ApiException;
import kr.shademap.global.exception.ErrorCode;
import kr.shademap.global.validation.Identifiers;
import kr.shademap.release.domain.Release;
import kr.shademap.release.dto.response.ContextResponse;
import kr.shademap.release.repository.ReleaseRepository;
import org.springframework.stereotype.Service;

@Service
public class ReleaseService {
    public static final String LIMITATION =
            "위치 검수 중인 실제 자료입니다. 참고 위치는 출입구·대기면·휴식 지점의 확정을 의미하지 않으며 그늘 분석과 경로 추천은 준비 중입니다.";

    private final ReleaseRepository repository;

    public ReleaseService(ReleaseRepository repository) {
        this.repository = repository;
    }

    public Release active() {
        var releases = repository.findActive();
        if (releases.size() != 1) {
            throw new ApiException(ErrorCode.RESOURCE_UNAVAILABLE, "조회 자료 등록을 준비하고 있습니다.");
        }
        return releases.getFirst();
    }

    public Release requireActive(String releaseId) {
        var id = Identifiers.uuid(releaseId);
        var release = active();
        if (!release.id().equals(id)) {
            throw new ApiException(ErrorCode.VERSION_CHANGED, "자료 버전이 변경되었습니다. 화면을 새로고침해주세요.");
        }
        return release;
    }

    public ContextResponse context(Release release, String requestId) {
        return new ContextResponse(
                requestId, release.id(), null, null, release.fingerprint(),
                "REAL", "REVIEW", Instant.now().toString(), "SNAPSHOT", List.of(LIMITATION)
        );
    }
}
