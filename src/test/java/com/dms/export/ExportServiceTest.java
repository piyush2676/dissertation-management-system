package com.dms.export;

import com.dms.allocation.Allocation;
import com.dms.allocation.AllocationService;
import com.dms.allocation.AllocationStatus;
import com.dms.outcome.OutcomeBoard;
import com.dms.outcome.OutcomeIndexing;
import com.dms.outcome.OutcomeKind;
import com.dms.outcome.OutcomeService;
import com.dms.outcome.OutcomeStatus;
import com.dms.session.AcademicSession;
import com.dms.topic.ExpectedOutcome;
import com.dms.topic.Topic;
import com.dms.topic.TopicStatus;
import com.dms.user.Programme;
import com.dms.user.StudentProfile;
import com.dms.user.SupervisorProfile;
import com.dms.user.User;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportServiceTest {

    @Mock private AllocationService allocationService;
    @Mock private OutcomeService outcomeService;

    @InjectMocks private ExportService service;

    @Test
    void aTitleWithACommaStaysOneColumn() {
        // The whole reason every field is quoted: the office opens this in a
        // spreadsheet, and a split title would silently shift every column after it.
        String csv = ExportService.csv(List.of(
                new String[]{"Thesis_ID", "Thesis Title"},
                new String[]{"MT26-001", "Scheduling, caching and the edge"}));

        assertTrue(csv.contains("\"MT26-001\",\"Scheduling, caching and the edge\""));
    }

    @Test
    void aQuoteInsideAFieldIsDoubledNotDropped() {
        String csv = ExportService.csv(List.<String[]>of(new String[]{"A \"quoted\" title"}));
        assertEquals("\"A \"\"quoted\"\" title\"\r\n", csv);
    }

    @Test
    void aNullFieldBecomesAnEmptyColumnRatherThanTheWordNull() {
        assertEquals("\"\",\"x\"\r\n", ExportService.csv(List.<String[]>of(new String[]{null, "x"})));
    }

    @Test
    void format4CarriesTheThesisIdTitleScholarAndSupervisors() {
        Allocation allocation = allocation();
        when(allocationService.cohortFor(Programme.MTECH)).thenReturn(List.of(allocation));
        when(outcomeService.countedRowsFor(allocation)).thenReturn(List.of());

        String csv = service.format4(Programme.MTECH);

        assertTrue(csv.startsWith("\"Thesis_ID\",\"Thesis Title\",\"Scholar Name\",\"Roll No\""));
        assertTrue(csv.contains("\"MT26-001\""));
        assertTrue(csv.contains("\"Avika Singh\""));
        assertTrue(csv.contains("\"Dr A Sharma & Dr B Pandey\""), "both supervisors on one line");
    }

    @Test
    void onlyVerifiedOutcomesReachTheOffice() {
        Allocation allocation = allocation();
        when(allocationService.cohortFor(Programme.MTECH)).thenReturn(List.of(allocation));
        when(outcomeService.countedRowsFor(allocation)).thenReturn(List.of(
                new OutcomeBoard.Row(1L, OutcomeKind.JOURNAL_PAPER, "A paper", "IEEE Access",
                        OutcomeIndexing.SCOPUS, OutcomeStatus.PUBLISHED, "10.1000/xyz",
                        LocalDate.now(), null, true, "PG Coordinator", Instant.now(), null)));

        String csv = service.format4(Programme.MTECH);

        assertTrue(csv.contains("Journal paper (Scopus, Published)"),
                "countedRowsFor is already verified-and-achieved only; an export is what the department stands behind");
    }

    @Test
    void aStudentWhoIsNotPlacedIsNotOnTheList() {
        Allocation withdrawn = allocation();
        withdrawn.setStatus(AllocationStatus.WITHDRAWN);
        when(allocationService.cohortFor(Programme.MTECH)).thenReturn(List.of(withdrawn));

        String csv = service.format4(Programme.MTECH);

        assertFalse(csv.contains("Avika Singh"));
    }

    @Test
    void noActiveSessionGivesAHeaderRatherThanAnError() {
        when(allocationService.cohortFor(Programme.MTECH)).thenThrow(new IllegalStateException("no session"));

        String csv = service.format5(Programme.MTECH);

        assertTrue(csv.startsWith("\"S.No\""));
        assertEquals(1, csv.split("\r\n").length);
    }

    @Test
    void format5CarriesThePhaseDomainAndOutcomesTheAccreditationPaperworkWants() {
        Allocation allocation = allocation();
        when(allocationService.cohortFor(Programme.MTECH)).thenReturn(List.of(allocation));
        lenient().when(outcomeService.countedRowsFor(allocation)).thenReturn(List.of());

        String csv = service.format5(Programme.MTECH);

        assertTrue(csv.contains("\"Final Dissertation\""));
        assertTrue(csv.contains("\"Edge computing\""));
        assertTrue(csv.contains("Research paper publication"));
        assertTrue(csv.contains("\"SDG 9\""));
    }

    // ---- fixtures -----------------------------------------------------------

    private static User user(Long id, String name) {
        User user = new User();
        user.setId(id);
        user.setEmail("user" + id + "@college.edu");
        user.setFullName(name);
        return user;
    }

    private static Allocation allocation() {
        StudentProfile student = new StudentProfile();
        student.setId(1L);
        student.setRollNo("24MCS001");
        student.setProgramme(Programme.MTECH);
        student.setSemester(4);
        student.setUser(user(1L, "Avika Singh"));

        SupervisorProfile guide = new SupervisorProfile();
        guide.setId(7L);
        guide.setUser(user(7L, "Dr A Sharma"));
        SupervisorProfile co = new SupervisorProfile();
        co.setId(8L);
        co.setUser(user(8L, "Dr B Pandey"));

        Topic topic = new Topic();
        topic.setId(10L);
        topic.setThesisCode("MT26-001");
        topic.setTitle("Adaptive load balancing");
        topic.setResearchDomain("Edge computing");
        topic.setSdgAlignment("SDG 9");
        topic.setStatus(TopicStatus.APPROVED);
        topic.setExpectedOutcomes(EnumSet.of(ExpectedOutcome.RESEARCH_PAPER));

        AcademicSession session = new AcademicSession();
        session.setId(30L);
        session.setLabel("2026-27");

        Allocation allocation = new Allocation();
        allocation.setId(11L);
        allocation.setStudent(student);
        allocation.setSupervisor(guide);
        allocation.setCoSupervisor(co);
        allocation.setTopic(topic);
        allocation.setSession(session);
        allocation.setStatus(AllocationStatus.ACCEPTED);
        return allocation;
    }
}
