package com.dms.web;

import java.time.LocalDate;

/**
 * What each role sees on landing. Views rather than entities, assembled inside a
 * read-only transaction, for the same reason every other board in the system is.
 */
public final class Dashboards {

    private Dashboards() {
    }

    public record Student(
            String sessionLabel,
            String topicTitle,
            String topicStatus,
            String guideName,
            String allocationStatus,
            long milestonesTotal,
            long milestonesFiled,
            long milestonesApproved,
            long withGuide,
            long openComments,
            String nextMilestone,
            LocalDate nextDueDate) {

        public boolean hasTopic() {
            return topicStatus != null;
        }

        public boolean hasGuide() {
            return guideName != null;
        }

        /** Which step of the pipeline the student is actually on, 1-based. */
        public int stage() {
            if (!hasTopic()) {
                return 1;
            }
            if (!"APPROVED".equals(topicStatus)) {
                return 1;
            }
            if (!hasGuide()) {
                return 2;
            }
            return milestonesFiled == 0 ? 3 : 4;
        }
    }

    public record Supervisor(
            long pendingTopics,
            long pendingGuideRequests,
            long pendingSubmissions,
            long studentsSupervised,
            int capacity) {

        public long seatsLeft() {
            return Math.max(0, capacity - studentsSupervised);
        }

        public boolean isFull() {
            return studentsSupervised >= capacity;
        }

        public long totalWaiting() {
            return pendingTopics + pendingGuideRequests + pendingSubmissions;
        }
    }

    public record Coordinator(
            String sessionLabel,
            long students,
            long placed,
            long unallocated,
            long guides,
            long guidesFull,
            long submissionsFiled) {

        public long placedPercent() {
            return students == 0 ? 0 : Math.round(placed * 100.0 / students);
        }
    }

    public record Admin(
            long users,
            long students,
            long guides,
            long topics,
            long allocations,
            long submissions,
            long submissionVersions,
            long auditEntries) {
    }
}
