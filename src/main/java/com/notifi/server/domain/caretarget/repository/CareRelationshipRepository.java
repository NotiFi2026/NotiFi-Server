package com.notifi.server.domain.caretarget.repository;

import com.notifi.server.domain.caretarget.entity.CareRelationship;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CareRelationshipRepository extends JpaRepository<CareRelationship, Long> {

    @Query("SELECT cr FROM CareRelationship cr JOIN FETCH cr.careTarget WHERE cr.userId = :userId")
    Page<CareRelationship> findByUserIdWithCareTarget(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT cr FROM CareRelationship cr JOIN FETCH cr.careTarget WHERE cr.userId = :userId AND cr.careTarget.id = :careTargetId")
    Optional<CareRelationship> findByUserIdAndCareTargetId(@Param("userId") Long userId, @Param("careTargetId") Long careTargetId);

    // careTarget 명시 조인 필수 — FK 역참조만 쓰면 @SQLRestriction(soft delete)이 적용되지 않아
    // 삭제된 노인이 D·S·E 계열 API에서 계속 조회된다
    @Query("SELECT CASE WHEN COUNT(cr) > 0 THEN TRUE ELSE FALSE END FROM CareRelationship cr JOIN cr.careTarget ct WHERE cr.userId = :userId AND ct.id = :careTargetId")
    boolean existsByUserIdAndCareTargetId(@Param("userId") Long userId, @Param("careTargetId") Long careTargetId);

    // careTarget 조인으로 soft delete 적용 — 삭제된 노인에겐 보호자 알림이 나가지 않는다
    @Query("SELECT cr FROM CareRelationship cr JOIN cr.careTarget ct WHERE ct.id = :careTargetId ORDER BY cr.notifyPriority ASC, cr.id ASC")
    List<CareRelationship> findGuardiansByCareTargetId(@Param("careTargetId") Long careTargetId);
}
