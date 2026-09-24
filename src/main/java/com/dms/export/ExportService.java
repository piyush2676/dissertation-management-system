package com.dms.export;

import com.dms.allocation.Allocation;
import com.dms.allocation.AllocationService;
import com.dms.outcome.OutcomeBoard;
import com.dms.outcome.OutcomeService;
import com.dms.session.DissertationPhase;
import com.dms.topic.Topic;
import com.dms.user.Programme;
import com.dms.user.StudentProfile;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Format 4 and Format 5 of the guidelines: the lists the department sends to the
 * Office of Director Academics (sections 4.13 and 5.6).
 *
 * <p>CSV rather than a rendered table, because the office puts these into a
 * spreadsheet. Written by hand: one escape rule, no dependency, and the reader
 * can see exactly what leaves the building.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExportService {

    private final AllocationService allocationService;
    private final OutcomeService outcomeService;

    /** Format 4: Thesis ID, title, scholar, supervisors, outcome, status. */
    public String format4(Programme programme) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Thesis_ID", "Thesis Title", "Scholar Name", "Roll No",
                "Supervisor(s)", "Outcome", "Status"});
        for (Allocation allocation : placed(programme)) {
            Topic topic = allocation.getTopic();
            rows.add(new String[]{
                    topic == null ? "" : nullToEmpty(topic.getThesisCode()),
                    topic == null ? "" : topic.getTitle(),
                    allocation.getStudent().getUser().getFullName(),
                    allocation.getStudent().getRollNo(),
                    supervisors(allocation),
                    outcomes(allocation),
                    topic == null ? "Not proposed" : topic.getStatus().name()
            });
        }
        return csv(rows);
    }

    /**
     * Format 5: the fuller allocation sheet, with the mapping columns the
     * accreditation paperwork wants beside each scholar.
     */
    public String format5(Programme programme) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"S.No", "Thesis_ID", "Roll No", "Scholar Name", "Programme", "Semester",
                "Phase", "Research Domain", "Thesis Title", "Supervisor", "Co-Supervisor",
                "Expected Outcomes", "SDG Alignment", "Session"});
        int serial = 1;
        for (Allocation allocation : placed(programme)) {
            StudentProfile student = allocation.getStudent();
            Topic topic = allocation.getTopic();
            DissertationPhase phase = DissertationPhase
                    .forSemester(student.getProgramme(), student.getSemester()).orElse(null);
            rows.add(new String[]{
                    String.valueOf(serial++),
                    topic == null ? "" : nullToEmpty(topic.getThesisCode()),
                    student.getRollNo(),
                    student.getUser().getFullName(),
                    student.getProgramme().name().replace('_', ' '),
                    student.getSemester() == null ? "" : String.valueOf(student.getSemester()),
                    phase == null ? "" : phase.getLabel(),
                    topic == null ? "" : nullToEmpty(topic.getResearchDomain()),
                    topic == null ? "" : topic.getTitle(),
                    allocation.getSupervisor().getUser().getFullName(),
                    allocation.getCoSupervisor() == null ? ""
                            : allocation.getCoSupervisor().getUser().getFullName(),
                    topic == null || topic.getExpectedOutcomes() == null ? ""
                            : topic.getExpectedOutcomes().stream()
                                    .map(o -> o.getLabel()).collect(Collectors.joining("; ")),
                    topic == null ? "" : nullToEmpty(topic.getSdgAlignment()),
                    allocation.getSession().getLabel()
            });
        }
        return csv(rows);
    }

    private List<Allocation> placed(Programme programme) {
        try {
            return allocationService.cohortFor(programme).stream()
                    .filter(a -> a.getStatus().occupiesASeat())
                    .toList();
        } catch (IllegalStateException ex) {
            // No active session for this programme: an empty sheet, not an error page.
            return List.of();
        }
    }

    /** Only verified outcomes are listed: an export is what the department stands behind. */
    private String outcomes(Allocation allocation) {
        List<OutcomeBoard.Row> counted = outcomeService.countedRowsFor(allocation);
        if (counted.isEmpty()) {
            return "";
        }
        return counted.stream()
                .map(o -> o.kind().getLabel() + " (" + o.indexing().getLabel() + ", " + o.status().getLabel() + ")")
                .collect(Collectors.joining("; "));
    }

    private static String supervisors(Allocation allocation) {
        String primary = allocation.getSupervisor().getUser().getFullName();
        return allocation.getCoSupervisor() == null ? primary
                : primary + " & " + allocation.getCoSupervisor().getUser().getFullName();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * RFC 4180: quote every field, double any quote inside it. Quoting
     * unconditionally is what keeps a thesis title with a comma from becoming two
     * columns in the office's spreadsheet.
     */
    static String csv(List<String[]> rows) {
        StringBuilder out = new StringBuilder();
        for (String[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                if (i > 0) {
                    out.append(',');
                }
                out.append('"').append(defuse(row[i]).replace("\"", "\"\"")).append('"');
            }
            out.append("\r\n");
        }
        return out.toString();
    }

    /**
     * Excel runs a cell that starts with = + - or @ as a formula, and titles and
     * names are typed by students. A leading apostrophe makes it plain text -- the
     * OWASP rule for CSV injection.
     */
    static String defuse(String cell) {
        if (cell == null || cell.isEmpty()) {
            return "";
        }
        char first = cell.charAt(0);
        return first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r'
                ? "'" + cell : cell;
    }
}
