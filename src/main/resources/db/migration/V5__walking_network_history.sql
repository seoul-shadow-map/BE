CREATE TABLE shade_api.walking_trial_revision (
    release_id uuid NOT NULL REFERENCES shade_api.release(id),
    network_id text NOT NULL,
    graph jsonb NOT NULL,
    metadata jsonb NOT NULL,
    registered_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (release_id, network_id)
);
INSERT INTO shade_api.walking_trial_revision(release_id,network_id,graph,metadata)
SELECT release_id,network_id,graph,metadata FROM shade_api.walking_trial;
GRANT SELECT ON shade_api.walking_trial_revision TO shadow_api;
