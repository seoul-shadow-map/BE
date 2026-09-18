package kr.shademap.walking.service;

import java.util.*;
import kr.shademap.catalog.service.SnapshotService;
import kr.shademap.global.exception.ApiException;
import kr.shademap.global.exception.ErrorCode;
import kr.shademap.release.service.ReleaseService;
import kr.shademap.walking.dto.ExposureRequest;
import kr.shademap.walking.dto.WalkingRequest;
import kr.shademap.walking.repository.ExposureRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExposureService {
    private final ExposureRepository repository;
    private final ReleaseService releases;
    private final SnapshotService snapshots;
    public ExposureService(ExposureRepository repository, ReleaseService releases, SnapshotService snapshots) {
        this.repository=repository; this.releases=releases; this.snapshots=snapshots;
    }
    @Transactional(readOnly=true, timeout=30)
    public Map<String,Object> analyze(ExposureRequest request) {
        if(request==null||request.geometry()==null||!"LineString".equals(request.geometry().type())
                ||request.routeKey()==null||request.routeKey().length()>100||request.snapshotId()==null)
            throw ApiException.input("경로와 분석 시각이 필요합니다.");
        var points=request.geometry().coordinates();
        if(points==null||points.size()<2||points.size()>10000)throw ApiException.input("경로 좌표 수가 허용 범위를 벗어났습니다.");
        double degrees=0;
        for(int i=0;i<points.size();i++) {
            var p=points.get(i);
            if(p==null||p.size()!=2)throw ApiException.input("잘못된 경로 좌표입니다.");
            WalkingService.validate(new WalkingRequest.Point(p.get(0),p.get(1)));
            if(i>0)degrees+=Math.hypot(p.get(0)-points.get(i-1).get(0),p.get(1)-points.get(i-1).get(1));
        }
        if(degrees>0.5)throw ApiException.input("분석 가능한 경로 길이를 초과했습니다.");
        var release=releases.requireActive(request.releaseId());
        var snapshot=snapshots.optional(release.id(),request.snapshotId());
        if(!repository.network(release.id(),request.networkId()))throw new ApiException(ErrorCode.VERSION_CHANGED,"보행망 버전이 변경되었습니다.");
        var pieces=repository.analyze(release.id(),snapshot.id(),request.geometry());
        if(pieces.isEmpty())throw ApiException.input("분석 가능한 경로가 없습니다.");
        double shade=0,sun=0,unknown=0;
        var segments=new ArrayList<Map<String,Object>>();
        for(var p:pieces) {
            switch(p.exposure()){case "SHADE"->shade+=p.lengthM();case "SUN"->sun+=p.lengthM();default->unknown+=p.lengthM();}
            segments.add(Map.of("sequence",segments.size(),"kind","NETWORK","geometry",p.geometry(),"exposure",p.exposure(),"lengthM",p.lengthM()));
        }
        double total=shade+sun+unknown, valid=shade+sun;
        var metrics=new LinkedHashMap<String,Object>();
        metrics.put("totalLengthM",total);metrics.put("shadeLengthM",shade);metrics.put("sunLengthM",sun);metrics.put("unknownLengthM",unknown);
        metrics.put("shadeRatio",valid>0?shade/valid:null);metrics.put("coverage",total>0?valid/total:null);metrics.put("expectedDurationSec",null);
        return Map.of("releaseId",release.id(),"networkId",request.networkId(),"snapshotId",snapshot.id(),"instant",snapshot.instant(),
            "routeKey",request.routeKey(),"status",unknown>1e-6?"PARTIAL_DATA":"READY","metrics",metrics,"segments",segments,
            "limitations",List.of("건물·수목 높이를 포함한 DSM 원본 EPSG:5186, 2m 픽셀 기준 경로 길이 분석입니다.",
                "선택 시각을 경로 전체에 적용하며 이동 중 변화·신호 대기·실제 보행면 높이는 반영하지 않습니다.","NoData 및 원본 범위 밖은 미확인 길이에 포함합니다."));
    }
}
