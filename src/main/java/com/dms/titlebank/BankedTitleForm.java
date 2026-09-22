package com.dms.titlebank;

import com.dms.topic.ExpectedOutcome;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/** Format 3: the columns a guide fills in for each title they offer. */
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BankedTitleForm {

    Long id;

    @NotBlank(message = "Give the title")
    @Size(max = 255, message = "Title must be 255 characters or fewer")
    String title;

    @NotBlank(message = "A three or four line abstract is what a scholar chooses on")
    @Size(min = 80, max = 2000, message = "Abstract must be between 80 and 2000 characters")
    String abstractText;

    @NotBlank(message = "Name the domain or technology")
    @Size(max = 128, message = "Domain must be 128 characters or fewer")
    String domain;

    @NotNull(message = "What should this produce?")
    ExpectedOutcome expectedOutcome;

    @NotNull(message = "How demanding is it?")
    Complexity complexity;

    public static BankedTitleForm form(BankedTitle banked) {
        BankedTitleForm form = new BankedTitleForm();
        form.id = banked.getId();
        form.title = banked.getTitle();
        form.abstractText = banked.getAbstractText();
        form.domain = banked.getDomain();
        form.expectedOutcome = banked.getExpectedOutcome();
        form.complexity = banked.getComplexity();
        return form;
    }
}
