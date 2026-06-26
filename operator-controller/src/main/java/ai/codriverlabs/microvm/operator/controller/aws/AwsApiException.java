package ai.codriverlabs.microvm.operator.controller.aws;

/**
 * Wraps AWS API errors with classification for retry logic.
 */
public class AwsApiException extends RuntimeException {

    public enum ErrorType {
        RETRYABLE,      // 429, 5xx, timeout
        NON_RETRYABLE,  // 400, 403, validation errors
        AUTH_FAILURE,    // credential expired/unavailable
        NOT_FOUND       // resource doesn't exist
    }

    private final ErrorType errorType;
    private final String requestId;
    private final int statusCode;

    public AwsApiException(String message, ErrorType errorType, String requestId, int statusCode) {
        super(message);
        this.errorType = errorType;
        this.requestId = requestId;
        this.statusCode = statusCode;
    }

    public AwsApiException(String message, Throwable cause, ErrorType errorType, String requestId, int statusCode) {
        super(message, cause);
        this.errorType = errorType;
        this.requestId = requestId;
        this.statusCode = statusCode;
    }

    public ErrorType getErrorType() { return errorType; }
    public String getRequestId() { return requestId; }
    public int getStatusCode() { return statusCode; }

    public boolean isRetryable() { return errorType == ErrorType.RETRYABLE; }
    public boolean isNotFound() { return errorType == ErrorType.NOT_FOUND; }
    public boolean isAuthFailure() { return errorType == ErrorType.AUTH_FAILURE; }
}
