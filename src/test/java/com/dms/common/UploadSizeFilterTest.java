package com.dms.common;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;

class UploadSizeFilterTest {

    private final UploadSizeFilter filter = new UploadSizeFilter(DataSize.ofMegabytes(25));

    private MockHttpServletResponse run(String contentType, long length) throws Exception {
        // The mock derives its length from the body, and a test should not allocate 26MB.
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/student/submissions/1/upload") {
            @Override
            public long getContentLengthLong() {
                return length;
            }
        };
        request.setContentType(contentType);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void anUploadWithinTheLimitPassesThrough() throws Exception {
        assertThat(run("multipart/form-data; boundary=x", 10L * 1024 * 1024).getStatus()).isEqualTo(200);
    }

    @Test
    void anUploadJustOverTheLimitIsStoppedWith413() throws Exception {
        assertThat(run("multipart/form-data; boundary=x", 26L * 1024 * 1024).getStatus())
                .isEqualTo(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
    }

    @Test
    void theEnvelopeAroundTheFileIsAllowedFor() throws Exception {
        // 25MB of file plus multipart framing is still a legal upload.
        assertThat(run("multipart/form-data; boundary=x", 25L * 1024 * 1024 + 1024).getStatus()).isEqualTo(200);
    }

    @Test
    void aLargeNonMultipartBodyIsNotThisFiltersBusiness() throws Exception {
        assertThat(run("application/x-www-form-urlencoded", 50L * 1024 * 1024).getStatus()).isEqualTo(200);
    }
}
