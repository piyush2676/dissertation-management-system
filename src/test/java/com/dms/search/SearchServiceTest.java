package com.dms.search;

import com.dms.allocation.AllocationRepository;
import com.dms.allocation.AllocationStatus;
import com.dms.submission.SubmissionRepository;
import com.dms.topic.TopicRepository;
import com.dms.user.StudentProfileRepository;
import com.dms.user.UserRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The point of these tests is not that search finds things. It is that a role
 * never reaches a repository method wider than its own scope: the unscoped
 * queries must not even be called for a student or a guide.
 */
@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    private static final String STUDENT = "student@college.edu";
    private static final String GUIDE = "guide@college.edu";

    @Mock private TopicRepository topicRepository;
    @Mock private AllocationRepository allocationRepository;
    @Mock private SubmissionRepository submissionRepository;
    @Mock private StudentProfileRepository studentProfileRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private SearchService service;

    @Test
    void aStudentOnlySearchesTheirOwnRecords() {
        when(topicRepository.searchOwnedBy("edge", STUDENT)).thenReturn(List.of());
        when(submissionRepository.searchOwnedBy("edge", STUDENT)).thenReturn(List.of());

        service.search("edge", STUDENT, Set.of("ROLE_STUDENT"));

        verify(topicRepository).searchOwnedBy("edge", STUDENT);
        verify(topicRepository, never()).searchAll(anyString());
        verifyNoInteractions(studentProfileRepository);
        verifyNoInteractions(userRepository);
    }

    @Test
    void aGuideSearchesOnlyWhatTheySupervise() {
        when(allocationRepository.searchSupervised(anyString(), anyString(), any())).thenReturn(List.of());
        when(topicRepository.searchProposedTo("edge", GUIDE)).thenReturn(List.of());
        when(submissionRepository.searchSupervised("edge", GUIDE)).thenReturn(List.of());

        service.search("edge", GUIDE, Set.of("ROLE_SUPERVISOR"));

        verify(allocationRepository).searchSupervised("edge", GUIDE, AllocationStatus.OCCUPIES_A_SEAT);
        verify(topicRepository, never()).searchAll(anyString());
        verifyNoInteractions(studentProfileRepository);
        verifyNoInteractions(userRepository);
    }

    @Test
    void aGuidesStudentSearchCountsOnlySeatHolders() {
        when(allocationRepository.searchSupervised(anyString(), anyString(), any())).thenReturn(List.of());
        when(topicRepository.searchProposedTo(anyString(), anyString())).thenReturn(List.of());
        when(submissionRepository.searchSupervised(anyString(), anyString())).thenReturn(List.of());

        service.search("avika", GUIDE, Set.of("ROLE_SUPERVISOR"));

        // A merely REQUESTED allocation is not supervision, so it must not be searchable.
        verify(allocationRepository).searchSupervised("avika", GUIDE, AllocationStatus.OCCUPIES_A_SEAT);
    }

    @Test
    void theCoordinatorReachesTheWholeCohortButNotAccounts() {
        when(studentProfileRepository.search("avika")).thenReturn(List.of());
        when(topicRepository.searchAll("avika")).thenReturn(List.of());

        service.search("avika", "coordinator@college.edu", Set.of("ROLE_COORDINATOR"));

        verify(studentProfileRepository).search("avika");
        verify(topicRepository).searchAll("avika");
        verifyNoInteractions(userRepository);
    }

    @Test
    void onlyTheAdminSearchesAccounts() {
        when(studentProfileRepository.search(anyString())).thenReturn(List.of());
        when(topicRepository.searchAll(anyString())).thenReturn(List.of());
        when(userRepository.search("sharma")).thenReturn(List.of());

        service.search("sharma", "admin@college.edu", Set.of("ROLE_ADMIN"));

        verify(userRepository).search("sharma");
    }

    @Test
    void aOneCharacterQueryTouchesNothing() {
        SearchResults results = service.search("a", STUDENT, Set.of("ROLE_STUDENT"));

        assertTrue(results.isEmpty());
        verifyNoInteractions(topicRepository, allocationRepository, submissionRepository,
                studentProfileRepository, userRepository);
    }

    @Test
    void aBlankOrNullQueryTouchesNothing() {
        assertTrue(service.search("   ", STUDENT, Set.of("ROLE_STUDENT")).isEmpty());
        assertTrue(service.search(null, STUDENT, Set.of("ROLE_STUDENT")).isEmpty());

        verifyNoInteractions(topicRepository, allocationRepository, submissionRepository,
                studentProfileRepository, userRepository);
    }

    @Test
    void theQueryIsTrimmedBeforeItIsUsed() {
        when(topicRepository.searchOwnedBy("edge", STUDENT)).thenReturn(List.of());
        when(submissionRepository.searchOwnedBy("edge", STUDENT)).thenReturn(List.of());

        SearchResults results = service.search("  edge  ", STUDENT, Set.of("ROLE_STUDENT"));

        assertEquals("edge", results.query());
        verify(topicRepository).searchOwnedBy("edge", STUDENT);
    }

    @Test
    void emptyGroupsAreDroppedRatherThanRenderedAsHeadings() {
        when(topicRepository.searchOwnedBy(anyString(), anyString())).thenReturn(List.of());
        when(submissionRepository.searchOwnedBy(anyString(), anyString())).thenReturn(List.of());

        SearchResults results = service.search("edge", STUDENT, Set.of("ROLE_STUDENT"));

        assertTrue(results.groups().isEmpty(), "a group with no hits is noise, not a result");
        assertTrue(results.isEmpty());
    }

    @Test
    void someoneHoldingTwoRolesSearchesBothScopes() {
        when(topicRepository.searchOwnedBy(anyString(), anyString())).thenReturn(List.of());
        when(submissionRepository.searchOwnedBy(anyString(), anyString())).thenReturn(List.of());
        when(allocationRepository.searchSupervised(anyString(), anyString(), any())).thenReturn(List.of());
        when(topicRepository.searchProposedTo(anyString(), anyString())).thenReturn(List.of());
        when(submissionRepository.searchSupervised(anyString(), anyString())).thenReturn(List.of());

        service.search("edge", GUIDE, Set.of("ROLE_STUDENT", "ROLE_SUPERVISOR"));

        verify(topicRepository).searchOwnedBy("edge", GUIDE);
        verify(topicRepository).searchProposedTo("edge", GUIDE);
        verify(topicRepository, never()).searchAll(anyString());
    }
}
