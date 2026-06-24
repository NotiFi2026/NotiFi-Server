package com.notifi.server.domain.caretarget;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CareRelationshipRepository extends JpaRepository<CareRelationship, Long> {

    @Query("SELECT cr FROM CareRelationship cr JOIN FETCH cr.careTarget WHERE cr.userId = :userId")
    Page<CareRelationship> findByUserIdWithCareTarget(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT cr FROM CareRelationship cr JOIN FETCH cr.careTarget WHERE cr.userId = :userId AND cr.careTarget.id = :careTargetId")
    Optional<CareRelationship> findByUserIdAndCareTargetId(@Param("userId") Long userId, @Param("careTargetId") Long careTargetId);
}
