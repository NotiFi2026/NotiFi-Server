package com.notifi.server.domain.caretarget.repository;

import com.notifi.server.domain.caretarget.entity.CareTarget;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareTargetRepository extends JpaRepository<CareTarget, Long> {
}
