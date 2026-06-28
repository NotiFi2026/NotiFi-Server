package com.notifi.server.domain.sensing.repository;

import com.notifi.server.domain.sensing.entity.PoseClip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PoseClipRepository extends JpaRepository<PoseClip, Long> {

    Optional<PoseClip> findBySensingEventId(Long sensingEventId);
}
