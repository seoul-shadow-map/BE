CREATE TABLE shade_api.walking_trial (
    release_id uuid PRIMARY KEY REFERENCES shade_api.release(id),
    network_id text NOT NULL,
    graph jsonb NOT NULL,
    metadata jsonb NOT NULL,
    CHECK (metadata->>'mode' = 'LOCAL_TRIAL'),
    CHECK (metadata->>'navigationApproved' = 'false')
);
GRANT SELECT ON shade_api.walking_trial TO shadow_api;
