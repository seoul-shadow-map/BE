-- Review storage only. This schema does not publish a service release.
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS postgis_raster;
CREATE SCHEMA IF NOT EXISTS shade_review;
CREATE TABLE IF NOT EXISTS shade_review.dataset_version (
    id uuid PRIMARY KEY,
    content_sha256 text NOT NULL UNIQUE CHECK (length(content_sha256)=64),
    metadata jsonb NOT NULL
);
CREATE TABLE IF NOT EXISTS shade_review.artifact (
    id uuid PRIMARY KEY,
    source_path text NOT NULL,
    content_sha256 text NOT NULL CHECK (length(content_sha256)=64),
    kind text NOT NULL,
    row_count bigint NOT NULL CHECK (row_count>=0),
    loaded_at timestamptz NOT NULL DEFAULT now(),
    is_current boolean NOT NULL DEFAULT true,
    UNIQUE(source_path,content_sha256)
);
CREATE UNIQUE INDEX IF NOT EXISTS artifact_current_path ON shade_review.artifact(source_path) WHERE is_current;
CREATE TABLE IF NOT EXISTS shade_review.record (
    artifact_id uuid NOT NULL REFERENCES shade_review.artifact(id),
    row_number bigint NOT NULL,
    entity_id text,
    dataset_version_id uuid REFERENCES shade_review.dataset_version(id),
    payload jsonb NOT NULL,
    geom geometry(Geometry,5186),
    geometry_status text NOT NULL,
    analysis_eligible boolean NOT NULL DEFAULT false CHECK (NOT analysis_eligible),
    route_eligible boolean NOT NULL DEFAULT false CHECK (NOT route_eligible),
    PRIMARY KEY(artifact_id,row_number),
    CHECK (geom IS NULL OR ST_IsValid(geom))
);
CREATE INDEX IF NOT EXISTS record_geom ON shade_review.record USING gist(geom);
CREATE INDEX IF NOT EXISTS record_entity ON shade_review.record(entity_id);
CREATE INDEX IF NOT EXISTS record_version ON shade_review.record(dataset_version_id);
CREATE TABLE IF NOT EXISTS shade_review.shadow_raster (
    dataset_version_id uuid PRIMARY KEY REFERENCES shade_review.dataset_version(id),
    instant timestamptz NOT NULL UNIQUE,
    original_path text NOT NULL,
    analysis_path text NOT NULL,
    analysis_sha256 text NOT NULL,
    metadata jsonb NOT NULL,
    rast raster NOT NULL,
    analysis_eligible boolean NOT NULL DEFAULT false CHECK (NOT analysis_eligible),
    CHECK (ST_SRID(rast)=5186 AND ST_NumBands(rast)=1),
    CHECK (ST_ScaleX(rast)=2 AND ST_ScaleY(rast)=-2)
);
CREATE INDEX IF NOT EXISTS shadow_raster_extent ON shade_review.shadow_raster USING gist(ST_ConvexHull(rast));
CREATE OR REPLACE VIEW shade_review.current_record AS
SELECT a.kind,a.source_path,r.* FROM shade_review.record r
JOIN shade_review.artifact a ON a.id=r.artifact_id WHERE a.is_current;
CREATE OR REPLACE VIEW shade_review.place AS
SELECT entity_id::uuid id,dataset_version_id,payload->>'name' name,payload->>'kind' kind,
payload->>'reference_crs' reference_crs,payload->>'coordinate_role' coordinate_role,
geom,geometry_status,analysis_eligible,route_eligible,payload
FROM shade_review.current_record WHERE kind='place';
CREATE OR REPLACE VIEW shade_review.walk_node AS
SELECT entity_id::uuid id,(payload->>'routing_id')::bigint routing_id,geom,payload
FROM shade_review.current_record WHERE kind='walk_node';
CREATE OR REPLACE VIEW shade_review.walk_link AS
SELECT entity_id::uuid id,(payload->>'routing_id')::bigint routing_id,
NULLIF(payload->>'source','')::numeric::bigint source,
NULLIF(payload->>'target','')::numeric::bigint target,
NULLIF(payload->>'measured_length_m','')::double precision measured_length_m,
payload->>'review_reason' review_reason,geom,route_eligible,payload
FROM shade_review.current_record WHERE kind='walk_link';
CREATE OR REPLACE VIEW shade_review.network_quarantine AS
SELECT * FROM shade_review.walk_link
WHERE (payload->>'missing_source')::boolean OR (payload->>'missing_target')::boolean;
CREATE OR REPLACE VIEW shade_review.crossing AS
SELECT entity_id,geom,geometry_status,payload FROM shade_review.current_record WHERE kind='crossing';
CREATE OR REPLACE VIEW shade_review.public_space_boundary AS
SELECT entity_id,geom,analysis_eligible,payload FROM shade_review.current_record WHERE kind='public_space_boundary';
CREATE OR REPLACE VIEW shade_review.use_point AS
SELECT entity_id,geom,analysis_eligible,route_eligible,payload FROM shade_review.current_record WHERE kind='use_point';
CREATE OR REPLACE VIEW shade_review.point_time_result AS
SELECT payload->>'point_id' point_id,(payload->>'instant')::timestamptz instant,
payload->>'status' status,NULLIF(payload->>'value','')::integer value,payload
FROM shade_review.current_record WHERE kind='point_time_result';
COMMENT ON SCHEMA shade_review IS '검수 자료. 서비스 공개·실제 이용 지점·분석 정확도 승인을 의미하지 않음.';
