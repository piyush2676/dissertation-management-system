package com.dms.topic;

import com.dms.audit.DomainEvents;
import com.dms.common.InvalidStateTransitionException;
import com.dms.common.NotFoundException;
import com.dms.user.*;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class TopicService {
    private final TopicRepository topicRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final SupervisorProfileRepository supervisorProfileRepository;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Optional<Topic> currentTopicFor(String studentEmail) {
        StudentProfile studentProfile = student(studentEmail);
        return topicRepository.findFirstByStudentOrderByCreatedAtDesc(studentProfile);
    }

    @Transactional(readOnly = true)
    public List<Topic> pendingFor(String supervisorEmail) {
        SupervisorProfile sup = sup(supervisorEmail);
        return topicRepository.findByProposedSupervisorAndStatusOrderByCreatedAtDesc(sup, TopicStatus.PROPOSED);
    }

    @Transactional(readOnly = true)
    public List<Topic> decidedBy(String supervisorEmail) {
        return topicRepository.findByProposedSupervisorOrderByCreatedAtDesc(sup(supervisorEmail)).stream().filter(t -> t.getStatus() != TopicStatus.PROPOSED).toList();
    }

    @Transactional(readOnly = true)
    public List<SupervisorProfile> selectableSupervisors() {
        return supervisorProfileRepository.findAllBy();
    }

    public Topic saveDraft(String studentEmail, TopicForm form) {
        StudentProfile student = student(studentEmail);
        Topic topic;
        if (form.getId() == null) {
            topic = new Topic();
            topic.setStudent(student);
            topic.setStatus(TopicStatus.DRAFT);
        } else {
            topic = loadOwned(form.getId(), studentEmail);
            if (topic.getStatus() != TopicStatus.DRAFT && topic.getStatus() != TopicStatus.CHANGES_REQUESTED) {
                throw new InvalidStateTransitionException(topic.getStatus(), TopicStatus.DRAFT);
            }
        }
        SupervisorProfile sup = supervisorProfileRepository.findById(form.getProposedSupervisorId())
                .orElseThrow(() -> new NotFoundException("Supervisor", form.getProposedSupervisorId()));
        topic.setTitle(form.getTitle().strip());
        topic.setAbstractText(form.getAbstractText().strip());
        topic.setKeywords(form.getKeywords() == null ? null : form.getKeywords().strip());
        topic.setProposedSupervisor(sup);
        return topicRepository.save(topic);

    }
    public Topic propose(String studentEmail,Long topicId) {
        Topic topic = loadOwned(topicId,studentEmail);
        if(!topic.getStatus().canTransitionTo(TopicStatus.PROPOSED)){
            throw new InvalidStateTransitionException(topic.getStatus(), TopicStatus.PROPOSED);
        }
        boolean resubmission = topic.getStatus() == TopicStatus.CHANGES_REQUESTED;
        if(resubmission){
            topic.setVersion(topic.getVersion()+1);
        }
        topic.setDecisionReason(null);
        topic.setDecidedBy(null);
        topic.setDecidedAt(null);
        topic.setStatus(TopicStatus.PROPOSED);
        Topic proposed = topicRepository.save(topic);
        events.publishEvent(new DomainEvents.TopicProposed(
                studentEmail, proposed.getId(), proposed.getTitle()));
        return proposed;
    }
    public Topic submitForApproval(String studentEmail,TopicForm form) {
        Topic saved = saveDraft(studentEmail,form);
        return propose(studentEmail,saved.getId());
    }
    public Topic decide(String supervisorEmail,Long topicId,TopicDecisionForm form) {
        Topic topic = topicRepository.findById(topicId).orElseThrow(() -> new NotFoundException("Topic", topicId));
        SupervisorProfile sup = sup(supervisorEmail);
        if (topic.getProposedSupervisor() == null || !sup.getId().equals(topic.getProposedSupervisor().getId())) {
            throw new NotFoundException("Topic", topicId);
        }
        TopicStatus target = form.getDecision();
        TopicStatus from = topic.getStatus();
        if (!topic.getStatus().canTransitionTo(target)) {
            throw new InvalidStateTransitionException(topic.getStatus(), target);
        }
        topic.setStatus(target);
        topic.setDecisionReason(target == TopicStatus.APPROVED ? null : form.getReason().strip());
        topic.setDecidedBy(sup.getUser());
        topic.setDecidedAt(Instant.now());
        Topic decided = topicRepository.save(topic);
        events.publishEvent(new DomainEvents.TopicDecided(
                supervisorEmail, decided.getId(), from.name(), target.name()));
        return decided;
    }

    private StudentProfile student(String email) {
        return studentProfileRepository.findByUserEmail(email)
                .orElseThrow(() -> new NotFoundException("Student profile for " + email + " not found"));
    }

    private SupervisorProfile sup(String email) {
        return supervisorProfileRepository.findByUserEmail(email)
                .orElseThrow(() -> new NotFoundException("Supervisor profile for " + email + " not found"));
    }

    private Topic loadOwned(Long topicId, String studentEmail) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new NotFoundException("Topic", topicId));
        if (!topicRepository.existsByIdAndStudentUserEmail(topicId, studentEmail)) {
            throw new NotFoundException("Topic", topicId);
        }
        return topic;
    }
}
