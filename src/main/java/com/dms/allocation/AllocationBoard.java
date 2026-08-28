package com.dms.allocation;

import com.dms.topic.TopicStatus;
import com.dms.user.Programme;

import java.time.Instant;
import java.util.List;

public record AllocationBoard(Programme programme, String sessionLabel, List<UnallocatedRow> unallocated, List<AllocatedRow> allocated, List<SupervisorLoad> supervisors) {
    public record UnallocatedRow(Long studentId, String rollNo, String fullName, Integer semester, String topicTitle, TopicStatus topicStatus) {
        public boolean topicApproved(){
            return topicStatus == TopicStatus.APPROVED;
        }
    }
    public record AllocatedRow(Long allocationId, String rollNo, String studentName, String supervisorName, AllocationStatus status, String topicTitle, Instant decidedAt){

    }
    public record SupervisorLoad(Long supervisorId,String name,String designation,long taken,int max){
        public long remaining(){
            return Math.max(0,max-taken);
        }
        public boolean isFull(){
            return taken >= max;
        }
    }
    public boolean isEmptyCohort(){
        return unallocated.isEmpty() && allocated.isEmpty();
    }
}