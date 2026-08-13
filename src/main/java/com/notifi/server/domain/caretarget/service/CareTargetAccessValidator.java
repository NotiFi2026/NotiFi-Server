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
     *
     * <p><b>보장 범위</b>: 이것이 막는 것은 "보호자가 <i>자기 세션으로</i> 대신 누르는 것"이지
     * "보호자가 절대 못 하는 것"이 아니다. 주 보호자는 연결코드(R5)를 발급해 노인 세션을 얻을 수
     * 있다. 그래도 두는 이유는 주 보호자가 애초에 그 계정을 만들어 준 주체이고, 우회해서 얻는 것이
     * {@code resolution_type} 라벨 차이뿐이기 때문이다 — 이를 막으려면 노인의 유일한 복구 경로를
     * 없애야 하는데 그게 훨씬 나쁜 거래다.
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
