package kr.shademap.catalog.domain;

import java.time.Instant;
import java.util.UUID;

public record Snapshot(UUID id, Instant instant) {}
