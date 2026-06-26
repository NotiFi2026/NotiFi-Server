package com.notifi.server.domain.caretarget.service;

import com.notifi.server.domain.caretarget.dto.*;
import com.notifi.server.domain.caretarget.entity.CareRelationship;
import com.notifi.server.domain.caretarget.entity.CareTarget;
import com.notifi.server.domain.caretarget.entity.Gender;
import com.notifi.server.domain.caretarget.entity.RelationshipType;
import com.notifi.server.domain.caretarget.exception.RelationshipErrorCode;
import com.notifi.server.domain.caretarget.repository.CareRelationshipRepository;
import com.notifi.server.domain.caretarget.repository.CareTargetRepository;
import com.notifi.server.domain.caretarget.token.InviteCodePayload;
import com.notifi.server.domain.caretarget.token.InviteCodeStore;
import com.notifi.server.domain.user.entity.Role;
import com.notifi.server.domain.user.entity.User;
import com.notifi.server.domain.user.repository.UserRepository;
import com.notifi.server.global.exception.BusinessException;
import com.notifi.server.global.exception.CommonErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class RelationshipServiceTest {

    @Mock CareRelationshipRepository careRelationshipRepository;
    @Mock CareTargetRepository careTargetRepository;
    @Mock UserRepository userRepository;
    @Mock InviteCodeStore inviteCodeStore;

    @InjectMocks RelationshipService relationshipService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(relationshipService, "inviteLinkBaseUrl",
                "https://app.bloom-safety.app/invite");
    }

    // ── issueInviteCode (R1-a) ─────────────────────────────────────────────

    @Test
    @DisplayName("issueInviteCode: 주 보호자가 코드 발급하면 code + invite_url 반환")
    void issueInviteCode_success() {
        CareTarget ct = careTarget(45L);
        CareRelationship primary = CareRelationship.of(1L, ct, RelationshipType.FAMILY, true, (short) 1);
        given(careRelationshipRepository.findByUserIdAndCareTargetId(1L, 45L)).willReturn(Optional.of(primary));
        given(inviteCodeStore.issue(any())).willReturn("AB3CD7EF");
        given(inviteCodeStore.nextExpiresAt()).willReturn(Instant.now().plusSeconds(86400));

        InviteCodeCreateResponse resp = relationshipService.issueInviteCode(1L, 45L,
                new InviteCodeCreateRequest(RelationshipType.FAMILY, null));

        assertThat(resp.code()).isEqualTo("AB3CD7EF");
        assertThat(resp.inviteUrl()).isEqualTo("https://app.bloom-safety.app/invite/AB3CD7EF");
        assertThat(resp.expiresAt()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("issueInviteCode: 주 보호자 아닌 경우 → 403 ACCESS_DENIED")
    void issueInviteCode_nonPrimary_accessDenied() {
        CareTarget ct = careTarget(45L);
        CareRelationship nonPrimary = CareRelationship.of(2L, ct, RelationshipType.FAMILY, false, (short) 2);
        given(careRelationshipRepository.findByUserIdAndCareTargetId(2L, 45L)).willReturn(Optional.of(nonPrimary));

        assertThatThrownBy(() -> relationshipService.issueInviteCode(2L, 45L,
                new InviteCodeCreateRequest(RelationshipType.FAMILY, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.ACCESS_DENIED);
    }

    // ── previewInviteCode (R1-c) ───────────────────────────────────────────

    @Test
    @DisplayName("previewInviteCode: 유효 코드 → 노인·초대자 정보 반환, findAndDelete 호출 없음")
    void previewInviteCode_success_codeNotConsumed() {
        InviteCodePayload payload = new InviteCodePayload(45L, RelationshipType.FAMILY, (short) 1, 1L);
        CareTarget ct = careTarget(45L);
        User inviter = user(1L, "김보호");
        Instant expiresAt = Instant.now().plusSeconds(3600);

        given(inviteCodeStore.find("AB3CD7EF")).willReturn(Optional.of(payload));
        given(careTargetRepository.findById(45L)).willReturn(Optional.of(ct));
        given(userRepository.findById(1L)).willReturn(Optional.of(inviter));
        given(inviteCodeStore.expiresAt("AB3CD7EF")).willReturn(Optional.of(expiresAt));

        InvitePreviewResponse resp = relationshipService.previewInviteCode("AB3CD7EF");

        assertThat(resp.careTargetId()).isEqualTo(45L);
        assertThat(resp.careTargetName()).isEqualTo("박순자");
        assertThat(resp.inviterName()).isEqualTo("김보호");
        assertThat(resp.relationshipType()).isEqualTo(RelationshipType.FAMILY);
        assertThat(resp.expiresAt()).isEqualTo(expiresAt);
        then(inviteCodeStore).should(never()).findAndDelete(any());
    }

    @Test
    @DisplayName("previewInviteCode: 유효하지 않은 코드 → 404 INVALID_INVITE_CODE")
    void previewInviteCode_invalidCode() {
        given(inviteCodeStore.find("EXPIRED00")).willReturn(Optional.empty());

        assertThatThrownBy(() -> relationshipService.previewInviteCode("EXPIRED00"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RelationshipErrorCode.INVALID_INVITE_CODE);
    }

    @Test
    @DisplayName("previewInviteCode: 노인이 삭제된 경우 → 404 INVALID_INVITE_CODE")
    void previewInviteCode_careTargetGone() {
        InviteCodePayload payload = new InviteCodePayload(99L, RelationshipType.FAMILY, (short) 1, 1L);
        given(inviteCodeStore.find("AB3CD7EF")).willReturn(Optional.of(payload));
        given(careTargetRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> relationshipService.previewInviteCode("AB3CD7EF"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RelationshipErrorCode.INVALID_INVITE_CODE);
    }

    // ── acceptInviteCode (R1-b) ────────────────────────────────────────────

    @Test
    @DisplayName("acceptInviteCode: 유효 코드 수락 → is_primary=false로 관계 저장")
    void acceptInviteCode_success() {
        InviteCodePayload payload = new InviteCodePayload(45L, RelationshipType.FAMILY, (short) 2, 1L);
        CareTarget ct = careTarget(45L);

        given(inviteCodeStore.findAndDelete("AB3CD7EF")).willReturn(Optional.of(payload));
        given(careTargetRepository.findById(45L)).willReturn(Optional.of(ct));
        given(careRelationshipRepository.existsByUserIdAndCareTargetId(2L, 45L)).willReturn(false);
        given(careRelationshipRepository.save(any())).willAnswer(inv -> {
            CareRelationship cr = inv.getArgument(0);
            ReflectionTestUtils.setField(cr, "id", 7L);
            return cr;
        });

        InviteCodeAcceptResponse resp = relationshipService.acceptInviteCode(2L, "AB3CD7EF");

        assertThat(resp.relationshipId()).isEqualTo(7L);
        assertThat(resp.careTargetId()).isEqualTo(45L);
        then(careRelationshipRepository).should().save(
                argThat(cr -> !cr.isPrimary() && cr.getRelationshipType() == RelationshipType.FAMILY)
        );
    }

    @Test
    @DisplayName("acceptInviteCode: 만료·사용된 코드 → 404 INVALID_INVITE_CODE")
    void acceptInviteCode_invalidCode() {
        given(inviteCodeStore.findAndDelete("BADCODE0")).willReturn(Optional.empty());

        assertThatThrownBy(() -> relationshipService.acceptInviteCode(2L, "BADCODE0"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RelationshipErrorCode.INVALID_INVITE_CODE);
    }

    @Test
    @DisplayName("acceptInviteCode: 이미 보호자인 경우 → 409 RELATIONSHIP_ALREADY_EXISTS")
    void acceptInviteCode_alreadyGuardian() {
        InviteCodePayload payload = new InviteCodePayload(45L, RelationshipType.FAMILY, (short) 2, 1L);
        CareTarget ct = careTarget(45L);

        given(inviteCodeStore.findAndDelete("AB3CD7EF")).willReturn(Optional.of(payload));
        given(careTargetRepository.findById(45L)).willReturn(Optional.of(ct));
        given(careRelationshipRepository.existsByUserIdAndCareTargetId(2L, 45L)).willReturn(true);

        assertThatThrownBy(() -> relationshipService.acceptInviteCode(2L, "AB3CD7EF"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RelationshipErrorCode.RELATIONSHIP_ALREADY_EXISTS);
    }

    // ── getGuardians (R2) ─────────────────────────────────────────────────

    @Test
    @DisplayName("getGuardians: 관계 있는 보호자면 목록 반환 (notify_priority 오름차순)")
    void getGuardians_success() {
        CareTarget ct = careTarget(45L);
        CareRelationship cr1 = CareRelationship.of(1L, ct, RelationshipType.FAMILY, true, (short) 1);
        CareRelationship cr2 = CareRelationship.of(2L, ct, RelationshipType.FAMILY, false, (short) 2);
        ReflectionTestUtils.setField(cr1, "id", 1L);
        ReflectionTestUtils.setField(cr2, "id", 2L);

        User u1 = user(1L, "김보호");
        User u2 = user(2L, "이보호");

        given(careRelationshipRepository.findByUserIdAndCareTargetId(1L, 45L)).willReturn(Optional.of(cr1));
        given(careRelationshipRepository.findGuardiansByCareTargetId(45L)).willReturn(List.of(cr1, cr2));
        given(userRepository.findAllById(any())).willReturn(List.of(u1, u2));

        List<GuardianResponse> result = relationshipService.getGuardians(1L, 45L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).isPrimary()).isTrue();
        assertThat(result.get(1).name()).isEqualTo("이보호");
    }

    @Test
    @DisplayName("getGuardians: 관계 없고 노인 존재 → 403 ACCESS_DENIED")
    void getGuardians_noRelationship_accessDenied() {
        given(careRelationshipRepository.findByUserIdAndCareTargetId(1L, 45L)).willReturn(Optional.empty());
        given(careTargetRepository.existsById(45L)).willReturn(true);

        assertThatThrownBy(() -> relationshipService.getGuardians(1L, 45L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.ACCESS_DENIED);
    }

    // ── updateRelationship (R3) ────────────────────────────────────────────

    @Test
    @DisplayName("updateRelationship: 주 보호자가 우선순위 변경 → 변경값 반환")
    void updateRelationship_success() {
        CareTarget ct = careTarget(45L);
        CareRelationship target = CareRelationship.of(2L, ct, RelationshipType.FAMILY, false, (short) 1);
        ReflectionTestUtils.setField(target, "id", 7L);
        CareRelationship callerCr = CareRelationship.of(1L, ct, RelationshipType.FAMILY, true, (short) 1);

        given(careRelationshipRepository.findById(7L)).willReturn(Optional.of(target));
        given(careRelationshipRepository.findByUserIdAndCareTargetId(1L, 45L)).willReturn(Optional.of(callerCr));

        RelationshipResponse resp = relationshipService.updateRelationship(1L, 7L,
                new RelationshipUpdateRequest(null, (short) 3));

        assertThat(resp.notifyPriority()).isEqualTo((short) 3);
        assertThat(resp.isPrimary()).isFalse();
    }

    @Test
    @DisplayName("updateRelationship: 관계 없음 → 404 RELATIONSHIP_NOT_FOUND")
    void updateRelationship_notFound() {
        given(careRelationshipRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> relationshipService.updateRelationship(1L, 99L,
                new RelationshipUpdateRequest(null, (short) 2)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RelationshipErrorCode.RELATIONSHIP_NOT_FOUND);
    }

    @Test
    @DisplayName("updateRelationship: 주 보호자 아닌 호출자 → 403 ACCESS_DENIED")
    void updateRelationship_nonPrimary_accessDenied() {
        CareTarget ct = careTarget(45L);
        CareRelationship target = CareRelationship.of(2L, ct, RelationshipType.FAMILY, false, (short) 1);
        ReflectionTestUtils.setField(target, "id", 7L);
        CareRelationship callerCr = CareRelationship.of(1L, ct, RelationshipType.FAMILY, false, (short) 2);

        given(careRelationshipRepository.findById(7L)).willReturn(Optional.of(target));
        given(careRelationshipRepository.findByUserIdAndCareTargetId(1L, 45L)).willReturn(Optional.of(callerCr));

        assertThatThrownBy(() -> relationshipService.updateRelationship(1L, 7L,
                new RelationshipUpdateRequest(null, (short) 3)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.ACCESS_DENIED);
    }

    // ── deleteRelationship (R4) ────────────────────────────────────────────

    @Test
    @DisplayName("deleteRelationship: 주 보호자가 비-주 보호자 해제 → delete 호출")
    void deleteRelationship_success() {
        CareTarget ct = careTarget(45L);
        CareRelationship target = CareRelationship.of(2L, ct, RelationshipType.FAMILY, false, (short) 2);
        ReflectionTestUtils.setField(target, "id", 7L);
        CareRelationship callerCr = CareRelationship.of(1L, ct, RelationshipType.FAMILY, true, (short) 1);

        given(careRelationshipRepository.findById(7L)).willReturn(Optional.of(target));
        given(careRelationshipRepository.findByUserIdAndCareTargetId(1L, 45L)).willReturn(Optional.of(callerCr));

        relationshipService.deleteRelationship(1L, 7L);

        then(careRelationshipRepository).should().delete(target);
    }

    @Test
    @DisplayName("deleteRelationship: 주 보호자 해제 시도 → 409 CANNOT_DELETE_PRIMARY")
    void deleteRelationship_cannotDeletePrimary() {
        CareTarget ct = careTarget(45L);
        CareRelationship target = CareRelationship.of(1L, ct, RelationshipType.FAMILY, true, (short) 1);
        ReflectionTestUtils.setField(target, "id", 1L);
        CareRelationship callerCr = CareRelationship.of(1L, ct, RelationshipType.FAMILY, true, (short) 1);

        given(careRelationshipRepository.findById(1L)).willReturn(Optional.of(target));
        given(careRelationshipRepository.findByUserIdAndCareTargetId(1L, 45L)).willReturn(Optional.of(callerCr));

        assertThatThrownBy(() -> relationshipService.deleteRelationship(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RelationshipErrorCode.CANNOT_DELETE_PRIMARY);
    }

    @Test
    @DisplayName("deleteRelationship: 관계 없음 → 404 RELATIONSHIP_NOT_FOUND")
    void deleteRelationship_notFound() {
        given(careRelationshipRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> relationshipService.deleteRelationship(1L, 99L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RelationshipErrorCode.RELATIONSHIP_NOT_FOUND);
    }

    @Test
    @DisplayName("deleteRelationship: 주 보호자 아닌 호출자 → 403 ACCESS_DENIED")
    void deleteRelationship_nonPrimary_accessDenied() {
        CareTarget ct = careTarget(45L);
        CareRelationship target = CareRelationship.of(2L, ct, RelationshipType.FAMILY, false, (short) 2);
        ReflectionTestUtils.setField(target, "id", 7L);
        CareRelationship callerCr = CareRelationship.of(1L, ct, RelationshipType.FAMILY, false, (short) 2);

        given(careRelationshipRepository.findById(7L)).willReturn(Optional.of(target));
        given(careRelationshipRepository.findByUserIdAndCareTargetId(1L, 45L)).willReturn(Optional.of(callerCr));

        assertThatThrownBy(() -> relationshipService.deleteRelationship(1L, 7L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.ACCESS_DENIED);
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private CareTarget careTarget(Long id) {
        CareTarget ct = CareTarget.create("박순자", null, Gender.FEMALE, null, null);
        ReflectionTestUtils.setField(ct, "id", id);
        return ct;
    }

    private User user(Long id, String name) {
        User u = User.create("test@notifi.dev", "hashed", name, Role.GUARDIAN);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private static <T> T argThat(org.mockito.ArgumentMatcher<T> matcher) {
        return org.mockito.ArgumentMatchers.argThat(matcher);
    }
}
