CREATE TABLE shade_api.heatmap_run (
    release_id uuid NOT NULL REFERENCES shade_api.release(id),
    snapshot_id uuid NOT NULL REFERENCES shade_api.snapshot(id),
    policy_id uuid NOT NULL,
    source_sha256 text NOT NULL,
    policy jsonb NOT NULL,
    PRIMARY KEY (release_id, snapshot_id)
);

CREATE TABLE shade_api.heatmap_grid (
    release_id uuid NOT NULL REFERENCES shade_api.release(id),
    id uuid NOT NULL,
    geom geometry(MultiPolygon, 5186) NOT NULL,
    target_area_m2 double precision NOT NULL CHECK (target_area_m2 > 0),
    PRIMARY KEY (release_id, id)
);
CREATE INDEX heatmap_grid_geom ON shade_api.heatmap_grid USING gist(geom);

CREATE TABLE shade_api.heatmap_metric (
    release_id uuid NOT NULL,
    snapshot_id uuid NOT NULL,
    grid_id uuid NOT NULL,
    valid_area_m2 double precision NOT NULL CHECK (valid_area_m2 >= 0),
    shade_area_m2 double precision NOT NULL CHECK (shade_area_m2 >= 0 AND shade_area_m2 <= valid_area_m2),
    PRIMARY KEY (release_id, snapshot_id, grid_id),
    FOREIGN KEY (release_id, snapshot_id) REFERENCES shade_api.heatmap_run(release_id, snapshot_id),
    FOREIGN KEY (release_id, grid_id) REFERENCES shade_api.heatmap_grid(release_id, id)
);
GRANT SELECT ON shade_api.heatmap_run, shade_api.heatmap_grid, shade_api.heatmap_metric TO shadow_api;
