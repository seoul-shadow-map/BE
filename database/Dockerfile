FROM postgis/postgis:17-3.5@sha256:01a6a70e41e6c4467c8f55f6063555ed72db2d6662cd0d571040d42eadaeb6f6
RUN apt-get update \
    && apt-get install -y --no-install-recommends postgresql-17-pgrouting=3.8.0-1.pgdg110+1 \
    && rm -rf /var/lib/apt/lists/*
