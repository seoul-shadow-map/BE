package kr.shademap.release.domain;

import java.util.UUID;

public record Release(UUID id, String fingerprint) {}
