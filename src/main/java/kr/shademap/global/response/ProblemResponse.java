package kr.shademap.global.response;

import java.util.List;

public record ProblemResponse(
        String type,
        String title,
        int status,
        String code,
        String detail,
        String requestId,
        List<IssueResponse> issues
) {}
