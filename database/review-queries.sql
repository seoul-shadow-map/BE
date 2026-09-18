-- Connect to shadow_map, port 55433. All views below are review-only.
SELECT kind, sum(row_count) AS records
FROM shade_review.artifact WHERE is_current GROUP BY kind ORDER BY kind;

-- One reference place, with full evidence and unchanged original fields.
SELECT id,name,coordinate_role,reference_crs,geometry_status,
       ST_AsText(geom),payload->>'ars_id' AS ars_id,payload->>'review_reason' AS review_reason
FROM shade_review.place WHERE payload->>'ars_id'='23218';

-- Missing source/target references remain in the review store, not a usable graph.
SELECT count(*) FROM shade_review.network_quarantine;

-- Native 2 m EPSG:5186 rasters, 0=sunlit, 1=shadow, no source NoData sentinel.
SELECT instant AT TIME ZONE 'Asia/Seoul' AS kst,ST_SRID(rast),
       ST_Width(rast),ST_Height(rast),ST_BandNoDataValue(rast,1)
FROM shade_review.shadow_raster ORDER BY instant;

SELECT point_id,instant,status,value FROM shade_review.point_time_result
WHERE point_id LIKE 'bus-23218-%' ORDER BY instant,point_id;
