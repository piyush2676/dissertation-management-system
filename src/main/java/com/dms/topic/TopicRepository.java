package com.dms.topic;

import com.dms.user.StudentProfile;
import com.dms.user.SupervisorProfile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TopicRepository extends JpaRepository<Topic,Long> {
    List<Topic> findByStudentOrderByCreatedAtDesc(StudentProfile student);

    Optional<Topic> findFirstByStudentAndStatusInOrderByCreatedAtDesc(StudentProfile student, Collection<TopicStatus> statuses);

    boolean existsByStudentAndStatusIn(StudentProfile student, Collection<TopicStatus> statuses);
    boolean existsByStudent(StudentProfile student);

    long countByProposedSupervisorAndStatus(SupervisorProfile supervisor, TopicStatus status);

    boolean existsByIdAndStudentUserEmail(Long topicId, String email);

    boolean existsByIdAndProposedSupervisorUserEmail(Long topicId, String email);

    @EntityGraph(attributePaths = {"student", "student.user"})
    List<Topic> findByProposedSupervisorAndStatusOrderByCreatedAtDesc(SupervisorProfile supervisor, TopicStatus status);

    @EntityGraph(attributePaths = {"student", "student.user"})
    List<Topic> findByProposedSupervisorOrderByCreatedAtDesc(SupervisorProfile supervisor);
    @EntityGraph(attributePaths = {"student","student.user","proposedSupervisor","proposedSupervisor.user"})
    Optional<Topic> findWithGraphById(Long id);
    @EntityGraph(attributePaths = {"proposedSupervisor","proposedSupervisor.user"})
    Optional<Topic> findFirstByStudentOrderByCreatedAtDesc(StudentProfile student);
    @EntityGraph(attributePaths = "student")
    List<Topic> findByStudentInOrderByCreatedAtDesc(Collection<StudentProfile> students);

    @EntityGraph(attributePaths = {"student", "student.user"})
    List<Topic> findTop5ByStatusOrderByDecidedAtDesc(TopicStatus status);

    // ---- search -------------------------------------------------------------
    //
    // Each variant carries its own scope in the where clause rather than
    // filtering a wider result afterwards, so a caller cannot accidentally be
    // handed a row it must not see.

    @EntityGraph(attributePaths = {"student", "student.user"})
    @Query("""
           select t from Topic t
           where lower(t.title) like lower(concat('%', :q, '%'))
             and t.student.user.email = :email
           order by t.createdAt desc
           """)
    List<Topic> searchOwnedBy(@Param("q") String q, @Param("email") String email);

    @EntityGraph(attributePaths = {"student", "student.user"})
    @Query("""
           select t from Topic t
           where lower(t.title) like lower(concat('%', :q, '%'))
             and t.proposedSupervisor.user.email = :email
           order by t.createdAt desc
           """)
    List<Topic> searchProposedTo(@Param("q") String q, @Param("email") String email);

    @EntityGraph(attributePaths = {"student", "student.user"})
    @Query("""
           select t from Topic t
           where lower(t.title) like lower(concat('%', :q, '%'))
              or lower(t.student.rollNo) like lower(concat('%', :q, '%'))
              or lower(t.student.user.fullName) like lower(concat('%', :q, '%'))
           order by t.createdAt desc
           """)
    List<Topic> searchAll(@Param("q") String q);
}
