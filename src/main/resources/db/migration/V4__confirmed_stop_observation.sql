CREATE TABLE shade_api.stop_confirmation (
    release_id uuid NOT NULL,
    place_id uuid NOT NULL,
    confirmed_at timestamptz NOT NULL DEFAULT now(),
    method text NOT NULL CHECK (method = 'USER_CONFIRMED'),
    evidence text NOT NULL,
    geom geometry(Point,5186) NOT NULL,
    PRIMARY KEY (release_id, place_id),
    FOREIGN KEY (release_id, place_id) REFERENCES shade_api.place_reference(release_id,id)
);

CREATE TABLE shade_api.stop_observation (
    release_id uuid NOT NULL,
    place_id uuid NOT NULL,
    snapshot_id uuid NOT NULL REFERENCES shade_api.snapshot(id),
    source_sha256 text NOT NULL,
    state text NOT NULL CHECK (state IN ('SHADE','SUN','NO_DATA','OUTSIDE_COVERAGE')),
    PRIMARY KEY (release_id, place_id, snapshot_id),
    FOREIGN KEY (release_id, place_id) REFERENCES shade_api.stop_confirmation(release_id,place_id)
);
GRANT SELECT ON shade_api.stop_confirmation, shade_api.stop_observation TO shadow_api;
