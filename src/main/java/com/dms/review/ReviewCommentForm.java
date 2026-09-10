package com.dms.review;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ReviewCommentForm {

    private Long versionId;

    /** Optional. Left blank the remark is about the document as a whole. */
    @Min(value = 1, message = "Page numbers start at 1")
    private Integer pageNo;

    @NotBlank(message = "Write something before posting")
    @Size(max = 2000, message = "Keep the comment under 2000 characters")
    private String body;
}
