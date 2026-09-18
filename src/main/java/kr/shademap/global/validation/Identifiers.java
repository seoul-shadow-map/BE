package kr.shademap.global.validation;

import java.util.UUID;
import java.util.regex.Pattern;
import kr.shademap.global.exception.ApiException;

public final class Identifiers {
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
    );

    private Identifiers() {}

    public static UUID uuid(String value) {
        if (value == null || !UUID_PATTERN.matcher(value).matches()) {
            throw ApiException.input("UUID 형식이 올바르지 않습니다.");
        }
        return UUID.fromString(value);
    }
}
