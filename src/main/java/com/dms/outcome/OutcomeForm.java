package com.dms.outcome;

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

import java.time.LocalDate;

/** Annexure-6 (d) and (e), from the student's side. */
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OutcomeForm {

    Long id;

    @NotNull(message = "What kind of outcome is it?")
    OutcomeKind kind;

    @NotBlank(message = "Give the title")
    @Size(max = 255, message = "Title must be 255 characters or fewer")
    String title;

    @Size(max = 255, message = "Venue must be 255 characters or fewer")
    String venue;

    @NotNull(message = "Say where it is indexed, or that it is not")
    OutcomeIndexing indexing = OutcomeIndexing.NONE;

    @NotNull(message = "Where does it stand?")
    OutcomeStatus status;

    @Size(max = 255, message = "Reference must be 255 characters or fewer")
    String reference;

    @PastOrPresent(message = "The date cannot be in the future")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    LocalDate outcomeDate;

    @Size(max = 2000, message = "Keep the notes under 2000 characters")
    String notes;

    public static OutcomeForm form(Outcome outcome) {
        OutcomeForm form = new OutcomeForm();
        form.id = outcome.getId();
        form.kind = outcome.getKind();
        form.title = outcome.getTitle();
        form.venue = outcome.getVenue();
        form.indexing = outcome.getIndexing();
        form.status = outcome.getStatus();
        form.reference = outcome.getReference();
        form.outcomeDate = outcome.getOutcomeDate();
        form.notes = outcome.getNotes();
        return form;
    }
}
