package com.notifi.server.domain.sensing.repository;

import com.notifi.server.domain.sensing.entity.PoseClip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PoseClipRepository extends JpaRepository<PoseClip, Long> {

    Optional<PoseClip> findBySensingEventId(Long sensingEventId);

    // frames JSONB 로드 없이 클립 존재 여부만 확인 (S2 has_replay 일괄 조회용)
    @Query("select pc.sensingEventId from PoseClip pc where pc.sensingEventId in :ids")
    List<Long> findExistingSensingEventIds(@Param("ids") Collection<Long> ids);
}
