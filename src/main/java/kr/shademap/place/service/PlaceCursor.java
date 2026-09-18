package kr.shademap.place.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import kr.shademap.global.exception.ApiException;
import kr.shademap.global.validation.Identifiers;

public final class PlaceCursor {
    private PlaceCursor() {}

    public static String encode(UUID last, String binding) {
        byte[] payload = (last + "|" + hash(binding)).getBytes(StandardCharsets.UTF_8);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
    }

    public static UUID decode(String cursor, String binding) {
        if (cursor == null) {
            return new UUID(0, 0);
        }
        if (cursor.length() > 2048) {
            throw ApiException.input("페이지 커서가 너무 깁니다.");
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", -1);
            if (parts.length != 2 || !parts[1].equals(hash(binding))) {
                throw ApiException.input("검색 조건이 변경되었습니다. 첫 페이지부터 조회해주세요.");
            }
            return Identifiers.uuid(parts[0]);
        } catch (IllegalArgumentException exception) {
            throw ApiException.input("페이지 커서가 올바르지 않습니다.");
        }
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
