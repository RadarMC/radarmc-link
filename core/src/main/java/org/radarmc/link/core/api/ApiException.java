package org.radarmc.link.core.api;

import java.time.Duration;
import org.radarmc.link.core.text.Strings;

public final class ApiException extends Exception {
    private static final long serialVersionUID = 1L;

    public enum Kind {
        UNAUTHORIZED,
        FORBIDDEN,
        NOT_FOUND,
        RATE_LIMITED,
        BAD_REQUEST,
        SERVER,
        NETWORK
    }

    private final Kind kind;
    private final int status;
    private final String code;
    private final transient Duration retryAfter;

    public ApiException(Kind kind, int status, String code, String message, Duration retryAfter, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.status = status;
        this.code = code;
        this.retryAfter = retryAfter;
    }

    public Kind kind() {
        return kind;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Duration retryAfter() {
        return retryAfter;
    }

    public boolean transientFailure() {
        return kind == Kind.NETWORK || kind == Kind.SERVER || kind == Kind.RATE_LIMITED;
    }

    public boolean rejectedCredentials() {
        return kind == Kind.UNAUTHORIZED || kind == Kind.FORBIDDEN || kind == Kind.NOT_FOUND;
    }

    public String describe() {
        String base = status > 0 ? "HTTP " + status : "network error";
        String codePart = Strings.isBlank(code) ? "" : " " + code;
        String detail = Strings.isBlank(getMessage()) ? "" : ": " + getMessage();
        return base + codePart + detail;
    }
}
