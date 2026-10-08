package com.dms.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Answers an oversized upload with the 413 page before anything reads its body.
 *
 * <p>The CSRF token travels as a field of the multipart form, so Spring Security has to parse the
 * body to find it, and the size limit trips there, ahead of the dispatcher and any
 * {@code @ExceptionHandler}. The student then got a bare error page, or a dropped connection.
 * A browser states the length up front, so the check is made on that header and the body is
 * never touched. A client that sends no length falls back to the container's own limit.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UploadSizeFilter extends OncePerRequestFilter {

    /** A little over the limit, for the multipart framing around the file itself. */
    private static final long ENVELOPE_BYTES = 64 * 1024;

    private final long maxRequestBytes;

    public UploadSizeFilter(@Value("${spring.servlet.multipart.max-request-size:25MB}") DataSize maxRequestSize) {
        this.maxRequestBytes = maxRequestSize.toBytes();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String type = request.getContentType();
        boolean multipart = type != null && type.toLowerCase().startsWith("multipart/");
        if (multipart && request.getContentLengthLong() > maxRequestBytes + ENVELOPE_BYTES) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            return;
        }
        chain.doFilter(request, response);
    }
}
