package com.dms.topic;

import jakarta.validation.constraints.NotBlank;
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
public class TopicForm {
    Long id;
    @NotBlank(message = "Title is required")
    @Size(max = 255,message = "Title must be 255 characters or fewer")
    String title;
    @NotBlank(message = "Abstract is required")
    @Size(min = 120,max = 4000,message = "Abstract must be between 120 and 4000 characters")
    String abstractText;
    @Size(max = 512,message = "Keywords must be 512 characters or fewer")
    String keywords;
    @NotNull(message = "Choose a supervisor")
    Long proposedSupervisorId;

    public static TopicForm form(Topic topic){
        TopicForm topicForm = new TopicForm();
        topicForm.id = topic.getId();
        topicForm.title = topic.getTitle();
        topicForm.abstractText = topic.getAbstractText();
        topicForm.keywords = topic.getKeywords();
        topicForm.proposedSupervisorId = topic.getProposedSupervisor() == null ? null : topic.getProposedSupervisor().getId();
        return topicForm;

    }
}
