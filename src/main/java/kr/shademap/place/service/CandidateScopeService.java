package kr.shademap.place.service;

import java.util.List;
import java.util.UUID;
import kr.shademap.global.exception.ApiException;
import kr.shademap.global.validation.Identifiers;
import kr.shademap.place.dto.response.CandidateScopeResponse;
import kr.shademap.place.repository.CandidateScopeRepository;
import org.springframework.stereotype.Service;

@Service
public class CandidateScopeService {
    private final CandidateScopeRepository repository;

    public CandidateScopeService(CandidateScopeRepository repository) {
        this.repository = repository;
    }

    public List<CandidateScopeResponse> findAll(UUID releaseId) {
        return repository.findByRelease(releaseId);
    }

    public CandidateScopeResponse require(UUID releaseId, String scopeId) {
        UUID id = Identifiers.uuid(scopeId);
        return findAll(releaseId).stream().filter(scope -> scope.id().equals(id)).findFirst()
                .orElseThrow(() -> ApiException.input("위치 미확정 자료의 원본 범위를 선택해주세요."));
    }
}
