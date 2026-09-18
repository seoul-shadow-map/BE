CREATE TABLE shade_api.release (
    id uuid PRIMARY KEY,
    fingerprint text NOT NULL UNIQUE,
    created_at timestamptz NOT NULL DEFAULT now(),
    active boolean NOT NULL DEFAULT false,
    stage text NOT NULL DEFAULT 'REVIEW' CHECK(stage='REVIEW')
);
CREATE UNIQUE INDEX one_active_release ON shade_api.release(active) WHERE active;
CREATE TABLE shade_api.snapshot (
    id uuid PRIMARY KEY,
    release_id uuid NOT NULL REFERENCES shade_api.release(id),
    dataset_version_id uuid NOT NULL,
    instant timestamptz NOT NULL,
    local_date date NOT NULL,
    wmts jsonb NOT NULL,
    UNIQUE(release_id,instant)
);
CREATE TABLE shade_api.resource (
    id uuid PRIMARY KEY,
    release_id uuid NOT NULL REFERENCES shade_api.release(id),
    snapshot_id uuid REFERENCES shade_api.snapshot(id),
    kind text NOT NULL,
    body jsonb NOT NULL
);
-- Immutable per-release reference projection; a later review import cannot change old pages.
CREATE TABLE shade_api.place_reference (
    release_id uuid NOT NULL REFERENCES shade_api.release(id),
    id uuid NOT NULL,
    dataset_version_id uuid NOT NULL,
    name text NOT NULL,
    kind text NOT NULL CHECK(kind IN ('REST','STOP')),
    geom geometry(Point,5186),
    geometry_status text NOT NULL,
    canonical_id uuid NOT NULL,
    coordinate_role text,
    source_label text,
    source_identifier text,
    address text,
    evidence_url text,
    review_reason text,
    PRIMARY KEY(release_id,id)
);
CREATE INDEX place_reference_geom ON shade_api.place_reference USING gist(geom);
CREATE INDEX place_reference_page ON shade_api.place_reference(release_id,kind,id);
GRANT USAGE ON SCHEMA shade_api TO shadow_api;
GRANT SELECT ON ALL TABLES IN SCHEMA shade_api TO shadow_api;
