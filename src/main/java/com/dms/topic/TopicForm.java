package com.dms.topic;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.EnumSet;
import java.util.Set;

/** The Thesis Title Proposal Form (Annexure-1) with Annexure-2's outcome tick list folded in. */
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TopicForm {
    Long id;
    @NotBlank(message = "Title is required")
    @Size(max = 255,message = "Title must be 255 characters or fewer")
    String title;
    @NotBlank(message = "Research domain is required")
    @Size(max = 128, message = "Research domain must be 128 characters or fewer")
    String researchDomain;
    @NotBlank(message = "Abstract is required")
    @Size(min = 120,max = 4000,message = "Abstract must be between 120 and 4000 characters")
    String abstractText;
    @NotBlank(message = "State the research objectives")
    @Size(min = 40, max = 2000, message = "Objectives must be between 40 and 2000 characters")
    String objectives;
    @Size(max = 255, message = "SDG alignment must be 255 characters or fewer")
    String sdgAlignment;
    @NotEmpty(message = "Tick at least one expected outcome")
    Set<ExpectedOutcome> expectedOutcomes = EnumSet.noneOf(ExpectedOutcome.class);
    @Size(max = 512,message = "Keywords must be 512 characters or fewer")
    String keywords;
    @NotNull(message = "Choose a supervisor")
    Long proposedSupervisorId;

    public static TopicForm form(Topic topic){
        TopicForm topicForm = new TopicForm();
        topicForm.id = topic.getId();
        topicForm.title = topic.getTitle();
        topicForm.researchDomain = topic.getResearchDomain();
        topicForm.abstractText = topic.getAbstractText();
        topicForm.objectives = topic.getObjectives();
        topicForm.sdgAlignment = topic.getSdgAlignment();
        // EnumSet.copyOf refuses an empty plain Set, so build up rather than copy.
        topicForm.expectedOutcomes = EnumSet.noneOf(ExpectedOutcome.class);
        if (topic.getExpectedOutcomes() != null) {
            topicForm.expectedOutcomes.addAll(topic.getExpectedOutcomes());
        }
        topicForm.keywords = topic.getKeywords();
        topicForm.proposedSupervisorId = topic.getProposedSupervisor() == null ? null : topic.getProposedSupervisor().getId();
        return topicForm;

    }
}
