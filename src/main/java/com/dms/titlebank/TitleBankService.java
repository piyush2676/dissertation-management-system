package com.dms.titlebank;

import com.dms.audit.DomainEvents;
import com.dms.common.NotFoundException;
import com.dms.user.SupervisorProfile;
import com.dms.user.SupervisorProfileRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The faculty title bank. A guide offers titles (section 4.3); a scholar may take
 * one or bring their own (4.6), and taking one only prefills the proposal form.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TitleBankService {

    private final BankedTitleRepository titleRepository;
    private final SupervisorProfileRepository supervisorProfileRepository;
    private final ApplicationEventPublisher events;

    // ---- supervisor ---------------------------------------------------------

    @Transactional(readOnly = true)
    public List<BankedTitleView> mine(String supervisorEmail) {
        return titleRepository.findBySupervisorOrderByCreatedAtDesc(supervisor(supervisorEmail)).stream()
                .map(this::view)
                .toList();
    }

    /** How many a guide has on offer, against the three section 4.3 asks for. */
    @Transactional(readOnly = true)
    public long openCountFor(String supervisorEmail) {
        return titleRepository.countBySupervisorAndStatus(supervisor(supervisorEmail), BankedTitleStatus.OPEN);
    }

    public BankedTitle offer(String supervisorEmail, BankedTitleForm form) {
        BankedTitle banked = new BankedTitle();
        banked.setSupervisor(supervisor(supervisorEmail));
        banked.setStatus(BankedTitleStatus.OPEN);
        apply(banked, form);
        BankedTitle saved = titleRepository.save(banked);
        events.publishEvent(new DomainEvents.TitleBanked(supervisorEmail, saved.getId(), saved.getTitle()));
        return saved;
    }

    public BankedTitle revise(String supervisorEmail, Long titleId, BankedTitleForm form) {
        BankedTitle banked = loadOwned(titleId, supervisorEmail);
        apply(banked, form);
        return titleRepository.save(banked);
    }

    @Transactional(readOnly = true)
    public BankedTitle loadForEdit(String supervisorEmail, Long titleId) {
        return loadOwned(titleId, supervisorEmail);
    }

    /**
     * Takes a title off the list. A topic already proposed from it is untouched:
     * that proposal became the scholar's own the moment they made it.
     */
    public BankedTitle withdraw(String supervisorEmail, Long titleId) {
        BankedTitle banked = loadOwned(titleId, supervisorEmail);
        banked.setStatus(BankedTitleStatus.WITHDRAWN);
        BankedTitle saved = titleRepository.save(banked);
        events.publishEvent(new DomainEvents.TitleWithdrawn(supervisorEmail, saved.getId(), saved.getTitle()));
        return saved;
    }

    // ---- student ------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<BankedTitleView> open(String query) {
        List<BankedTitle> found = query == null || query.isBlank()
                ? titleRepository.findByStatusOrderByCreatedAtDesc(BankedTitleStatus.OPEN)
                : titleRepository.search(query.strip());
        return found.stream().map(this::view).toList();
    }

    /** One title, for prefilling the proposal form. Only an open one can be adopted. */
    @Transactional(readOnly = true)
    public BankedTitleView adoptable(Long titleId) {
        BankedTitle banked = titleRepository.findWithGraphById(titleId)
                .orElseThrow(() -> new NotFoundException("Title", titleId));
        if (banked.getStatus() != BankedTitleStatus.OPEN) {
            throw new NotFoundException("Title", titleId);
        }
        return view(banked);
    }

    // ---- helpers ------------------------------------------------------------

    private BankedTitleView view(BankedTitle t) {
        SupervisorProfile sup = t.getSupervisor();
        return new BankedTitleView(
                t.getId(), t.getTitle(), t.getAbstractText(), t.getDomain(),
                t.getExpectedOutcome(), t.getComplexity(), t.getStatus(),
                sup.getId(), sup.getUser().getFullName(), sup.getDesignation(),
                sup.getResearchInterests(), t.getCreatedAt());
    }

    private static void apply(BankedTitle banked, BankedTitleForm form) {
        banked.setTitle(form.getTitle().strip());
        banked.setAbstractText(form.getAbstractText().strip());
        banked.setDomain(form.getDomain().strip());
        banked.setExpectedOutcome(form.getExpectedOutcome());
        banked.setComplexity(form.getComplexity());
    }

    private SupervisorProfile supervisor(String email) {
        return supervisorProfileRepository.findByUserEmail(email)
                .orElseThrow(() -> new NotFoundException("Supervisor profile for " + email + " not found"));
    }

    private BankedTitle loadOwned(Long titleId, String supervisorEmail) {
        BankedTitle banked = titleRepository.findById(titleId)
                .orElseThrow(() -> new NotFoundException("Title", titleId));
        if (!titleRepository.existsByIdAndSupervisorUserEmail(titleId, supervisorEmail)) {
            throw new NotFoundException("Title", titleId);
        }
        return banked;
    }
}
