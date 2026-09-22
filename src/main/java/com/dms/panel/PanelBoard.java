package com.dms.panel;

import com.dms.user.Programme;

import java.time.Instant;
import java.util.List;

/** The coordinator's panel page: every placed student with their panel, and who is appointable. */
public record PanelBoard(
        Programme programme,
        String sessionLabel,
        List<Row> rows,
        List<MemberRow> appointable) {

    public record MemberRow(Long userId, String name, String email, Instant addedAt) {
    }

    public record Row(
            Long allocationId,
            String rollNo,
            String studentName,
            Long supervisorUserId,
            String supervisorName,
            Long coSupervisorUserId,
            String topicTitle,
            List<MemberRow> members) {

        public int size() {
            return members.size();
        }

        /** Two members is what the guidelines expect; fewer is worth flagging, not refusing. */
        public boolean complete() {
            return members.size() >= PanelService.EXPECTED_SIZE;
        }

        /** The guide and co-supervisor cannot be appointed here, so the page greys them out. */
        public boolean conflicts(Long userId) {
            return userId.equals(supervisorUserId)
                    || (coSupervisorUserId != null && coSupervisorUserId.equals(userId));
        }

        public boolean alreadyOn(Long userId) {
            return members.stream().anyMatch(m -> m.userId().equals(userId));
        }
    }

    public long panelsComplete() {
        return rows.stream().filter(Row::complete).count();
    }

    public boolean isEmptyCohort() {
        return rows.isEmpty();
    }
}
