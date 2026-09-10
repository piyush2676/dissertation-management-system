package com.dms.submission;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
@NoArgsConstructor
public class SubmissionUploadForm {

    /**
     * Emptiness, type and size are checked in the storage layer rather than here,
     * so one rule covers every future upload path.
     */
    private MultipartFile file;

    @Size(max = 500, message = "Keep the note under 500 characters")
    private String note;
}
