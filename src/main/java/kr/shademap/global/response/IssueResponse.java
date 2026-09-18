package kr.shademap.global.response;

public record IssueResponse(String code, String message, String field, boolean retryable) {
    public static IssueResponse of(String code, String message) {
        return new IssueResponse(code, message, null, false);
    }
}
