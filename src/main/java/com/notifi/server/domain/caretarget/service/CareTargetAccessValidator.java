package com.notifi.server.domain.caretarget.service;

import com.notifi.server.domain.caretarget.entity.CareRelationship;
import com.notifi.server.domain.caretarget.exception.CareTargetErrorCode;
import com.notifi.server.domain.caretarget.repository.CareRelationshipRepository;
import com.notifi.server.domain.caretarget.repository.CareTargetRepository;
import com.notifi.server.global.exception.BusinessException;
import com.notifi.server.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 노인(care_target) 접근권한 가드 공통 컴포넌트.
 * 판정 규칙:
 *   관계 없음 + 노인 존재 → 403 ACCESS_DENIED
 *   관계 없음 + 노인 없음(또는 soft-deleted) → 404 CARE_TARGET_NOT_FOUND
 */
@Component
@RequiredArgsConstructor
public class CareTargetAccessValidator {

    private final CareRelationshipRepository careRelationshipRepository;
    private final CareTargetRepository careTargetRepository;

    /** 보호자 관계 필수 (존재 여부만 확인). */
    public void requireRelationship(Long userId, Long careTargetId) {
        if (!careRelationshipRepository.existsByUserIdAndCareTargetId(userId, careTargetId)) {
            throw notRelatedException(careTargetId);
        }
    }

    /** 보호자 관계 필수 — 관계 엔티티가 필요한 호출부용. */
    public CareRelationship getRelationshipOrThrow(Long userId, Long careTargetId) {
        return careRelationshipRepository
                .findByUserIdAndCareTargetId(userId, careTargetId)
                .orElseThrow(() -> notRelatedException(careTargetId));
    }

    /** 주 보호자 관계 필수. */
    public void requirePrimary(Long userId, Long careTargetId) {
        CareRelationship cr = getRelationshipOrThrow(userId, careTargetId);
        if (!cr.isPrimary()) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }
    }

    /**
     * 노인 본인만 허용 — 보호자도 막는다.
     *
     * <p>"본인이 직접 괜찮다고 했다"가 성립해야 하는 경로에 쓴다. 보호자가 대신 눌러 줄 수 있으면
     * 그 의미가 사라지고, 보호자에겐 이미 자기 이름으로 해제하는 경로(E3)가 따로 있다.
     */
    public void requireSelf(Long userId, Long careTargetId) {
        Long linkedUserId = careTargetRepository.findById(careTargetId)
                .orElseThrow(() -> new BusinessException(CareTargetErrorCode.CARE_TARGET_NOT_FOUND))
                .getUserId();
        if (!userId.equals(linkedUserId)) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }
    }

    /** 보호자 관계 또는 노인 본인(care_target.user_id == userId) 허용. */
    public void requireRelationshipOrSelf(Long userId, Long careTargetId) {
        if (careRelationshipRepository.existsByUserIdAndCareTargetId(userId, careTargetId)) {
            return;
        }
        Long linkedUserId = careTargetRepository.findById(careTargetId)
                .orElseThrow(() -> new BusinessException(CareTargetErrorCode.CARE_TARGET_NOT_FOUND))
                .getUserId();
        if (!userId.equals(linkedUserId)) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }
    }

    private BusinessException notRelatedException(Long careTargetId) {
        if (careTargetRepository.existsById(careTargetId)) {
            return new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }
        return new BusinessException(CareTargetErrorCode.CARE_TARGET_NOT_FOUND);
    }
}
