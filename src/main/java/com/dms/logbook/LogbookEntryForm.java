package com.dms.logbook;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.time.ZoneId;

/** The student's side of an Annexure-4 row: when we met, what I was given, what I did. */
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LogbookEntryForm {

    Long id;

    @NotNull(message = "When did the meeting happen?")
    @PastOrPresent(message = "A meeting cannot be recorded before it happens")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    LocalDateTime meetingAt;

    @NotBlank(message = "Record what your guide assigned")
    @Size(max = 2000, message = "Keep the assigned work under 2000 characters")
    String workAssigned;

    @NotBlank(message = "Record what you completed")
    @Size(max = 2000, message = "Keep the completed work under 2000 characters")
    String workCompleted;

    @Size(max = 2000, message = "Keep the challenges under 2000 characters")
    String challenges;

    public static LogbookEntryForm form(LogbookEntry entry) {
        LogbookEntryForm form = new LogbookEntryForm();
        form.id = entry.getId();
        form.meetingAt = LocalDateTime.ofInstant(entry.getMeetingAt(), ZoneId.systemDefault());
        form.workAssigned = entry.getWorkAssigned();
        form.workCompleted = entry.getWorkCompleted();
        form.challenges = entry.getChallenges();
        return form;
    }
}
