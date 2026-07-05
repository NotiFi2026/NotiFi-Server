package com.notifi.server.domain.caretarget.service;

import com.notifi.server.domain.caretarget.dto.CareTargetCreateRequest;
import com.notifi.server.domain.caretarget.dto.CareTargetCreateResponse;
import com.notifi.server.domain.caretarget.dto.CareTargetDetailResponse;
import com.notifi.server.domain.caretarget.dto.CareTargetSummaryResponse;
import com.notifi.server.domain.caretarget.dto.CareTargetUpdateRequest;
import com.notifi.server.domain.caretarget.entity.CareRelationship;
import com.notifi.server.domain.caretarget.entity.CareTarget;
import com.notifi.server.domain.caretarget.entity.RelationshipType;
import com.notifi.server.domain.caretarget.repository.CareRelationshipRepository;
import com.notifi.server.domain.caretarget.repository.CareTargetRepository;
import com.notifi.server.domain.device.repository.DeviceRepository;
import com.notifi.server.domain.user.entity.Role;
import com.notifi.server.domain.user.entity.User;
import com.notifi.server.domain.user.repository.UserRepository;
import com.notifi.server.global.exception.BusinessException;
import com.notifi.server.global.exception.CommonErrorCode;
import com.notifi.server.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CareTargetService {

    private final CareTargetRepository careTargetRepository;
    private final CareRelationshipRepository careRelationshipRepository;
    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final CareTargetAccessValidator accessValidator;

    @Transactional
    public CareTargetCreateResponse register(Long userId, CareTargetCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));

        // 노인 계정은 보호 대상이지 보호자가 아니다 — 노인 등록(주 보호자 자동 연결) 차단
        if (user.getRole() == Role.CARE_RECIPIENT) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }

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

        List<Long> careTargetIds = page.getContent().stream()
                .map(cr -> cr.getCareTarget().getId())
                .toList();
        Map<Long, Integer> countMap = deviceRepository.deviceCountMap(careTargetIds);

        Page<CareTargetSummaryResponse> mapped = page.map(cr ->
                CareTargetSummaryResponse.from(cr, countMap.getOrDefault(cr.getCareTarget().getId(), 0)));
        return PageResponse.from(mapped);
    }

    @Transactional(readOnly = true)
    public CareTargetDetailResponse getDetail(Long userId, Long careTargetId) {
        CareRelationship cr = accessValidator.getRelationshipOrThrow(userId, careTargetId);
        return CareTargetDetailResponse.from(cr);
    }

    @Transactional
    public CareTargetDetailResponse update(Long userId, Long careTargetId, CareTargetUpdateRequest request) {
        CareRelationship cr = accessValidator.getRelationshipOrThrow(userId, careTargetId);
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
        CareRelationship cr = accessValidator.getRelationshipOrThrow(userId, careTargetId);
        if (!cr.isPrimary()) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }
        cr.getCareTarget().softDelete();
    }
}
