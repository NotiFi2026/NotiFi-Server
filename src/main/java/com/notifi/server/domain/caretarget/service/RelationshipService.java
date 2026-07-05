package com.notifi.server.domain.caretarget.service;

import com.notifi.server.domain.caretarget.dto.*;
import com.notifi.server.domain.caretarget.entity.CareRelationship;
import com.notifi.server.domain.caretarget.entity.CareTarget;
import com.notifi.server.domain.caretarget.exception.CareTargetErrorCode;
import com.notifi.server.domain.caretarget.exception.RelationshipErrorCode;
import com.notifi.server.domain.caretarget.repository.CareRelationshipRepository;
import com.notifi.server.domain.caretarget.repository.CareTargetRepository;
import com.notifi.server.domain.caretarget.token.InviteCodePayload;
import com.notifi.server.domain.caretarget.token.InviteCodeStore;
import com.notifi.server.domain.caretarget.token.RecipientCodePayload;
import com.notifi.server.domain.user.entity.Role;
import com.notifi.server.domain.user.entity.User;
import com.notifi.server.domain.user.repository.UserRepository;
import com.notifi.server.global.exception.BusinessException;
import com.notifi.server.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RelationshipService {

    private final CareRelationshipRepository careRelationshipRepository;
    private final CareTargetRepository careTargetRepository;
    private final UserRepository userRepository;
    private final InviteCodeStore inviteCodeStore;
    private final CareTargetAccessValidator accessValidator;

    @Value("${invite.link-base-url}")
    private String inviteLinkBaseUrl;

    // ── R1-a: 초대코드 발급 ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public InviteCodeCreateResponse issueInviteCode(Long userId, Long careTargetId,
                                                     InviteCodeCreateRequest request) {
        accessValidator.requirePrimary(userId, careTargetId);

        short priority = request.notifyPriority() != null ? request.notifyPriority() : 1;
        InviteCodePayload payload = new InviteCodePayload(careTargetId, request.relationshipType(),
                priority, userId);

        String code = inviteCodeStore.issue(payload);
        String inviteUrl = inviteLinkBaseUrl + "/" + code;
        return new InviteCodeCreateResponse(code, inviteUrl, inviteCodeStore.nextExpiresAt());
    }

    // ── R5: 노인 계정 연결코드 발급 ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public RecipientCodeCreateResponse issueRecipientCode(Long userId, Long careTargetId) {
        accessValidator.requirePrimary(userId, careTargetId);

        CareTarget careTarget = careTargetRepository.findById(careTargetId)
                .orElseThrow(() -> new BusinessException(CareTargetErrorCode.CARE_TARGET_NOT_FOUND));
        if (careTarget.getUserId() != null) {
            throw new BusinessException(CareTargetErrorCode.CARE_TARGET_ALREADY_LINKED);
        }

        String code = inviteCodeStore.issueRecipientCode(new RecipientCodePayload(careTargetId, userId));
        return new RecipientCodeCreateResponse(code, inviteCodeStore.nextExpiresAt());
    }

    // ── R1-c: 초대코드 미리보기 (코드 유지) ────────────────────────────────────

    @Transactional(readOnly = true)
    public InvitePreviewResponse previewInviteCode(String code) {
        InviteCodePayload payload = inviteCodeStore.find(code)
                .orElseThrow(() -> new BusinessException(RelationshipErrorCode.INVALID_INVITE_CODE));

        CareTarget careTarget = careTargetRepository.findById(payload.careTargetId())
                .orElseThrow(() -> new BusinessException(RelationshipErrorCode.INVALID_INVITE_CODE));

        String inviterName = userRepository.findById(payload.issuedBy())
                .map(u -> u.getName())
                .orElse(null);

        Instant expiresAt = inviteCodeStore.expiresAt(code).orElse(null);

        return new InvitePreviewResponse(careTarget.getId(), careTarget.getName(),
                inviterName, payload.relationshipType(), expiresAt);
    }

    // ── R1-b: 초대코드 수락 ──────────────────────────────────────────────────

    @Transactional
    public InviteCodeAcceptResponse acceptInviteCode(Long userId, String code) {
        // 노인 계정은 보호자가 될 수 없다 — 코드 소모(findAndDelete) 전에 차단
        User caller = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
        if (caller.getRole() == Role.CARE_RECIPIENT) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }

        InviteCodePayload payload = inviteCodeStore.findAndDelete(code)
                .orElseThrow(() -> new BusinessException(RelationshipErrorCode.INVALID_INVITE_CODE));

        CareTarget careTarget = careTargetRepository.findById(payload.careTargetId())
                .orElseThrow(() -> new BusinessException(RelationshipErrorCode.INVALID_INVITE_CODE));

        if (careRelationshipRepository.existsByUserIdAndCareTargetId(userId, careTarget.getId())) {
            throw new BusinessException(RelationshipErrorCode.RELATIONSHIP_ALREADY_EXISTS);
        }

        CareRelationship cr = CareRelationship.of(userId, careTarget,
                payload.relationshipType(), false, payload.notifyPriority());
        try {
            cr = careRelationshipRepository.save(cr);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(RelationshipErrorCode.RELATIONSHIP_ALREADY_EXISTS);
        }

        return new InviteCodeAcceptResponse(cr.getId(), careTarget.getId());
    }

    // ── R2: 보호자 목록 ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<GuardianResponse> getGuardians(Long userId, Long careTargetId) {
        accessValidator.getRelationshipOrThrow(userId, careTargetId);

        List<CareRelationship> relationships =
                careRelationshipRepository.findGuardiansByCareTargetId(careTargetId);

        List<Long> userIds = relationships.stream().map(CareRelationship::getUserId).toList();
        Map<Long, User> userMap = userRepository.findAllById(userIds)
                .stream().collect(Collectors.toMap(User::getId, u -> u));

        return relationships.stream()
                .map(cr -> GuardianResponse.from(cr, userMap.get(cr.getUserId())))
                .toList();
    }

    // ── R3: 관계 수정 ────────────────────────────────────────────────────────

    @Transactional
    public RelationshipResponse updateRelationship(Long userId, Long relationshipId,
                                                    RelationshipUpdateRequest request) {
        CareRelationship target = careRelationshipRepository.findById(relationshipId)
                .orElseThrow(() -> new BusinessException(RelationshipErrorCode.RELATIONSHIP_NOT_FOUND));

        accessValidator.requirePrimary(userId, target.getCareTarget().getId());
        target.update(request.relationshipType(), request.notifyPriority());
        return RelationshipResponse.from(target);
    }

    // ── R4: 연결 해제 ────────────────────────────────────────────────────────

    @Transactional
    public void deleteRelationship(Long userId, Long relationshipId) {
        CareRelationship target = careRelationshipRepository.findById(relationshipId)
                .orElseThrow(() -> new BusinessException(RelationshipErrorCode.RELATIONSHIP_NOT_FOUND));

        accessValidator.requirePrimary(userId, target.getCareTarget().getId());

        if (target.isPrimary()) {
            throw new BusinessException(RelationshipErrorCode.CANNOT_DELETE_PRIMARY);
        }

        careRelationshipRepository.delete(target);
    }
}
