package com.dms.recommendation;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RecommendationForm {

    @NotNull(message = "Tick one recommendation")
    Verdict verdict;

    @Size(max = 2000, message = "Keep this under 2000 characters")
    String organisation;

    @Size(max = 2000, message = "Keep this under 2000 characters")
    String technicalContent;

    @Size(max = 2000, message = "Keep this under 2000 characters")
    String strengths;

    @Size(max = 2000, message = "Keep this under 2000 characters")
    String queries;

    @Size(max = 2000, message = "Keep this under 2000 characters")
    String vivaQuestions;

    /**
     * Annexure-6 section 6 asks for the modifications in enough detail that the
     * candidate can respond to them, so a verdict that sends the thesis back has
     * to say what for.
     */
    @AssertTrue(message = "Say what needs changing when the thesis is not acceptable as it is")
    public boolean isQueriesPresentWhenSendingBack() {
        if (verdict == null || verdict == Verdict.ACCEPTABLE) {
            return true;
        }
        return queries != null && !queries.isBlank();
    }

    public static RecommendationForm form(Recommendation recommendation) {
        RecommendationForm form = new RecommendationForm();
        form.verdict = recommendation.getVerdict();
        form.organisation = recommendation.getOrganisation();
        form.technicalContent = recommendation.getTechnicalContent();
        form.strengths = recommendation.getStrengths();
        form.queries = recommendation.getQueries();
        form.vivaQuestions = recommendation.getVivaQuestions();
        return form;
    }
}
