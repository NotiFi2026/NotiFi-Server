package com.notifi.server.domain.caretarget;

import com.notifi.server.domain.caretarget.dto.CareTargetCreateRequest;
import com.notifi.server.domain.caretarget.dto.CareTargetCreateResponse;
import com.notifi.server.domain.caretarget.dto.CareTargetDetailResponse;
import com.notifi.server.domain.caretarget.dto.CareTargetSummaryResponse;
import com.notifi.server.domain.caretarget.dto.CareTargetUpdateRequest;
import com.notifi.server.domain.caretarget.exception.CareTargetErrorCode;
import com.notifi.server.domain.user.Role;
import com.notifi.server.domain.user.User;
import com.notifi.server.domain.user.UserRepository;
import com.notifi.server.global.exception.BusinessException;
import com.notifi.server.global.exception.CommonErrorCode;
import com.notifi.server.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CareTargetService {

    private final CareTargetRepository careTargetRepository;
    private final CareRelationshipRepository careRelationshipRepository;
    private final UserRepository userRepository;

    @Transactional
    public CareTargetCreateResponse register(Long userId, CareTargetCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));

        CareTarget careTarget = careTargetRepository.save(CareTarget.create(
                request.name(),
                request.birthDate(),
                request.gender(),
                request.address(),
                request.emergencyMemo()
        ));

        RelationshipType type = (user.getRole() == Role.SOCIAL_WORKER)
                ? RelationshipType.SOCIAL_WORKER
                : RelationshipType.FAMILY;

        CareRelationship relationship = CareRelationship.of(userId, careTarget, type, true, (short) 1);
        careRelationshipRepository.save(relationship);

        return new CareTargetCreateResponse(careTarget.getId());
    }

    @Transactional(readOnly = true)
    public PageResponse<CareTargetSummaryResponse> getMyCareTargets(Long userId, Pageable pageable) {
        Page<CareRelationship> page = careRelationshipRepository
                .findByUserIdWithCareTarget(userId, pageable);

        Page<CareTargetSummaryResponse> mapped = page.map(cr -> CareTargetSummaryResponse.from(cr));
        return PageResponse.from(mapped);
    }

    @Transactional(readOnly = true)
    public CareTargetDetailResponse getDetail(Long userId, Long careTargetId) {
        CareRelationship cr = getRelationshipOrThrow(userId, careTargetId);
        return CareTargetDetailResponse.from(cr);
    }

    @Transactional
    public CareTargetDetailResponse update(Long userId, Long careTargetId, CareTargetUpdateRequest request) {
        CareRelationship cr = getRelationshipOrThrow(userId, careTargetId);
        cr.getCareTarget().update(
                request.name(),
                request.birthDate(),
                request.gender(),
                request.address(),
                request.emergencyMemo()
        );
        return CareTargetDetailResponse.from(cr);
    }

    @Transactional
    public void delete(Long userId, Long careTargetId) {
        CareRelationship cr = getRelationshipOrThrow(userId, careTargetId);
        if (!cr.isPrimary()) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }
        cr.getCareTarget().softDelete();
    }

    // ── private ──────────────────────────────────────────────────────────────

    /**
     * 관계 기반 접근권한 가드.
     * 관계 없음 + 노인 존재 → 403 ACCESS_DENIED
     * 관계 없음 + 노인 없음(또는 soft-deleted) → 404 CARE_TARGET_NOT_FOUND
     */
    private CareRelationship getRelationshipOrThrow(Long userId, Long careTargetId) {
        return careRelationshipRepository
                .findByUserIdAndCareTargetId(userId, careTargetId)
                .orElseThrow(() -> {
                    if (careTargetRepository.existsById(careTargetId)) {
                        throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
                    }
                    throw new BusinessException(CareTargetErrorCode.CARE_TARGET_NOT_FOUND);
                });
    }
}
