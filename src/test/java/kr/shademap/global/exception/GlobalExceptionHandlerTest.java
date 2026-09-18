package kr.shademap.global.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import tools.jackson.databind.json.JsonMapper;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void databaseFailureDoesNotExposeSqlOrCredentials() {
        var exception = new DataAccessResourceFailureException("SELECT private_payload; password=do-not-expose");
        var result = handler.database(exception, request());

        assertEquals(503, result.getStatusCode().value());
        String body = JsonMapper.builder().build().writeValueAsString(result.getBody());
        assertFalse(body.contains("SELECT"));
        assertFalse(body.contains("do-not-expose"));
        assertEquals("RESOURCE_UNAVAILABLE", result.getBody().code());
        assertEquals("test-request-id", result.getBody().requestId());
    }

    @Test
    void unexpectedFailureUsesContractCodeAndSanitizedMessage() {
        var result = handler.unexpected(new IllegalStateException("secret internal details"), request());

        assertEquals(500, result.getStatusCode().value());
        assertEquals("REQUEST_FAILED", result.getBody().code());
        assertFalse(result.getBody().detail().contains("secret"));
        assertEquals("application/problem+json", result.getHeaders().getContentType().toString());
    }

    private MockHttpServletRequest request() {
        var request = new MockHttpServletRequest();
        request.setAttribute("requestId", "test-request-id");
        return request;
    }
}
