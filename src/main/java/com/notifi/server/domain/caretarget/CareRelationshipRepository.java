package com.notifi.server.domain.caretarget;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CareRelationshipRepository extends JpaRepository<CareRelationship, Long> {

    @Query("SELECT cr FROM CareRelationship cr JOIN FETCH cr.careTarget WHERE cr.userId = :userId")
    Page<CareRelationship> findByUserIdWithCareTarget(@Param("userId") Long userId, Pageable pageable);
}
