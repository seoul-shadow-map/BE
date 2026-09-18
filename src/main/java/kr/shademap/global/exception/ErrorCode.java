package kr.shademap.global.exception;

public enum ErrorCode {
    INVALID_INPUT(400),
    NOT_FOUND(404),
    METHOD_NOT_ALLOWED(405, "REQUEST_FAILED"),
    VERSION_CHANGED(409),
    INTERNAL_ERROR(500, "REQUEST_FAILED"),
    RESOURCE_UNAVAILABLE(503);

    private final int status;
    private final String code;

    ErrorCode(int status) {
        this.status = status;
        this.code = name();
    }

    ErrorCode(int status, String code) {
        this.status = status;
        this.code = code;
    }

    public String code() {
        return code;
    }

    public int status() {
        return status;
    }
}
