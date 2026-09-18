# 그늘지도 · BE

그늘지도의 조회 API와 PostgreSQL/PostGIS 검수 DB를 담당하는 Spring Boot 백엔드입니다. Java 21, Spring Boot 4, Gradle Wrapper, Flyway를 사용합니다. 조회 자료와 장소 좌표는 검수 단계이며 실제 길찾기 안내용으로 승인되지 않았습니다.

## 연결된 저장소

| 역할 | 저장소 |
| --- | --- |
| 웹 화면 | [FE](https://github.com/seoul-shadow-map/FE) |
| 조회 API·DB | [BE](https://github.com/seoul-shadow-map/BE) |
| 자료 처리·OpenAPI·WMTS | [data-pipeline](https://github.com/seoul-shadow-map/data-pipeline) |

세 저장소를 같은 상위 폴더에 `frontend`, `backend`, `data-pipeline` 이름으로 복제합니다. API 계약은 `../data-pipeline/contracts/openapi.yaml`에 있습니다. 적재·검증 스크립트는 이 폴더 배치를 기준으로 동작합니다.

## 로컬 실행

Java 21, Python 3.12 이상, Docker가 필요합니다. PowerShell에서 세 저장소의 상위 폴더로 이동한 뒤:

```powershell
python backend/database/init-env.py
./backend/database/db.ps1 start
./backend/run.ps1 start
```

`backend/.env`에는 DB 비밀번호가 생성되며 Git에서 제외됩니다. 환경에 따라 `API_DB_PASSWORD`와 `MIGRATION_DB_PASSWORD`는 `data-pipeline`의 적재 작업 및 `backend/database/prepare-api.py --roles`로 준비합니다. 기동 전 실제 데이터를 적재하려면 [data-pipeline](https://github.com/seoul-shadow-map/data-pipeline)의 README를 따릅니다.

```powershell
./backend/run.ps1 test
./backend/run.ps1 build
```

기본 API 주소는 `http://127.0.0.1:8080/api/v1`입니다. FE 개발 서버는 `/api/v1` 요청을 이 서버로 프록시합니다.

## 주요 API

| 경로 | 기능 |
| --- | --- |
| `GET /actuator/health` | 서버 상태 |
| `GET /api/v1/config` | 조회 버전과 기능 상태 |
| `GET /api/v1/catalog` | 제공 날짜와 시각 |
| `GET /api/v1/resources` | WMTS 및 공간 자료 메타데이터 |
| `GET /api/v1/places`, `GET /api/v1/stops` | 검수 중인 장소·정류장 조회 |
| `GET /api/v1/heatmap` | 격자별 그늘 면적 비율 |
| `GET /api/v1/walking-network`, `POST /api/v1/walking-routes` | 로컬 시험용 도보 경로 |

응답의 상세 필드와 제한은 [OpenAPI 계약](https://github.com/seoul-shadow-map/data-pipeline/blob/main/contracts/openapi.yaml)을 참고합니다.

## 데이터 제외

`backend/.gitignore`는 `.env`, Gradle 산출물, `backups/`의 DB 덤프·복구 기록, CSV/GIS 자료, 압축 파일을 제외합니다. 원본 자료와 생성 결과는 상위 폴더의 `data/`에 두며 어떤 저장소에도 추가하지 않습니다. 스키마와 Flyway 마이그레이션 SQL은 실행 코드이므로 포함됩니다.
