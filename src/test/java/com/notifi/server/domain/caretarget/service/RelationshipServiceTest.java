package com.notifi.server.domain.caretarget.service;

import com.notifi.server.domain.caretarget.dto.*;
import com.notifi.server.domain.caretarget.entity.CareRelationship;
import com.notifi.server.domain.caretarget.dto.RecipientCodeCreateResponse;
import com.notifi.server.domain.caretarget.entity.CareTarget;
import com.notifi.server.domain.caretarget.entity.Gender;
import com.notifi.server.domain.caretarget.entity.RelationshipType;
import com.notifi.server.domain.caretarget.exception.CareTargetErrorCode;
import com.notifi.server.domain.caretarget.exception.RelationshipErrorCode;
import com.notifi.server.domain.caretarget.repository.CareRelationshipRepository;
import com.notifi.server.domain.caretarget.repository.CareTargetRepository;
import com.notifi.server.domain.caretarget.token.InviteCodePayload;
import com.notifi.server.domain.caretarget.token.InviteCodeStore;
import com.notifi.server.domain.caretarget.token.InviteCodeProbeThrottle;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class RelationshipServiceTest {

    @Mock CareRelationshipRepository careRelationshipRepository;
    @Mock CareTargetRepository careTargetRepository;
    @Mock UserRepository userRepository;
    @Mock InviteCodeStore inviteCodeStore;
    @Mock InviteCodeProbeThrottle inviteCodeProbeThrottle;
    @Mock CareTargetAccessValidator accessValidator;

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
        willThrow(new BusinessException(CommonErrorCode.ACCESS_DENIED))
                .given(accessValidator).requirePrimary(2L, 45L);

        assertThatThrownBy(() -> relationshipService.issueInviteCode(2L, 45L,
                new InviteCodeCreateRequest(RelationshipType.FAMILY, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.ACCESS_DENIED);
    }

    // ── issueRecipientCode (R5) ────────────────────────────────────────────

    @Test
    @DisplayName("issueRecipientCode: 주 보호자가 미연결 노인 코드 발급 → code + expires_at 반환")
    void issueRecipientCode_success() {
        CareTarget ct = careTarget(45L);
        given(careTargetRepository.findById(45L)).willReturn(Optional.of(ct));
        given(inviteCodeStore.issueRecipientCode(any())).willReturn("RC3DE7FG");
        given(inviteCodeStore.nextExpiresAt()).willReturn(Instant.now().plusSeconds(86400));

        RecipientCodeCreateResponse resp = relationshipService.issueRecipientCode(1L, 45L);

        assertThat(resp.code()).isEqualTo("RC3DE7FG");
        assertThat(resp.expiresAt()).isAfter(Instant.now());
        then(inviteCodeStore).should().issueRecipientCode(argThat(p ->
                p.careTargetId() == 45L && p.issuedBy() == 1L));
    }

    @Test
    @DisplayName("issueRecipientCode: 주 보호자 아님 → 403 ACCESS_DENIED")
    void issueRecipientCode_nonPrimary_accessDenied() {
        willThrow(new BusinessException(CommonErrorCode.ACCESS_DENIED))
                .given(accessValidator).requirePrimary(2L, 45L);

        assertThatThrownBy(() -> relationshipService.issueRecipientCode(2L, 45L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.ACCESS_DENIED);
        then(inviteCodeStore).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("issueRecipientCode: 이미 연결된 노인에게도 발급한다 — 재로그인 복구 경로")
    void issueRecipientCode_alreadyLinked_stillIssues() {
        CareTarget ct = careTarget(45L);
        ct.linkUser(9L);
        given(careTargetRepository.findById(45L)).willReturn(Optional.of(ct));
        given(inviteCodeStore.issueRecipientCode(any())).willReturn("RC3DE7FG");
        given(inviteCodeStore.nextExpiresAt()).willReturn(Instant.parse("2026-08-14T00:00:00Z"));

        RecipientCodeCreateResponse res = relationshipService.issueRecipientCode(1L, 45L);

        // 여기서 막으면 세션이 끊긴 노인을 되살릴 방법이 보호자의 방문뿐이다.
        // 노인은 이메일·비밀번호를 모르고 재설정 경로도 없다.
        assertThat(res.code()).isEqualTo("RC3DE7FG");
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

        InvitePreviewResponse resp = relationshipService.previewInviteCode(7L, "AB3CD7EF");

        assertThat(resp.careTargetId()).isEqualTo(45L);
        assertThat(resp.careTargetName()).isEqualTo("박순자");
        assertThat(resp.inviterName()).isEqualTo("김보호");
        assertThat(resp.relationshipType()).isEqualTo(RelationshipType.FAMILY);
        assertThat(resp.expiresAt()).isEqualTo(expiresAt);
        then(inviteCodeStore).should(never()).findAndDelete(any());
        // 정상 사용자는 오타 몇 번이 다음 초대까지 따라가면 안 된다
        then(inviteCodeProbeThrottle).should().reset(7L);
    }

    @Test
    @DisplayName("previewInviteCode: 유효하지 않은 코드 → 404 INVALID_INVITE_CODE + 프로빙 1회 기록")
    void previewInviteCode_invalidCode() {
        given(inviteCodeStore.find("EXPIRED00")).willReturn(Optional.empty());

        assertThatThrownBy(() -> relationshipService.previewInviteCode(7L, "EXPIRED00"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RelationshipErrorCode.INVALID_INVITE_CODE);

        then(inviteCodeProbeThrottle).should().recordFailure(7L);
    }

    @Test
    @DisplayName("previewInviteCode: 프로빙 제한 초과 → 429, 코드 조회조차 하지 않는다")
    void previewInviteCode_throttled() {
        // 미리보기는 코드를 소모하지 않아 무제한 시도가 가능하다.
        // 맞히면 노인 이름·초대자 이름이 그대로 나간다
        given(inviteCodeProbeThrottle.isBlocked(7L)).willReturn(true);

        assertThatThrownBy(() -> relationshipService.previewInviteCode(7L, "AB3CD7EF"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RelationshipErrorCode.TOO_MANY_INVITE_ATTEMPTS);

        then(inviteCodeStore).should(never()).find(any());
    }

    @Test
    @DisplayName("previewInviteCode: 노인이 삭제된 경우 → 404 INVALID_INVITE_CODE, 단 카운터는 초기화")
    void previewInviteCode_careTargetGone_stillResetsCounter() {
        // 코드는 맞힌 사람이다. 여기서 카운터를 남기면 9회 실패한 사용자가
        // 유효하지만 삭제된 링크를 한 번 확인한 뒤 다음 오타 한 번에 차단된다
        InviteCodePayload payload = new InviteCodePayload(99L, RelationshipType.FAMILY, (short) 1, 1L);
        given(inviteCodeStore.find("AB3CD7EF")).willReturn(Optional.of(payload));
        given(careTargetRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> relationshipService.previewInviteCode(7L, "AB3CD7EF"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RelationshipErrorCode.INVALID_INVITE_CODE);

        then(inviteCodeProbeThrottle).should().reset(7L);
        then(inviteCodeProbeThrottle).should(never()).recordFailure(any());
    }

    @Test
    @DisplayName("previewInviteCode: 초대자가 탈퇴한 경우 → inviter_name null로 정상 반환")
    void previewInviteCode_inviterGone_inviterNameNull() {
        InviteCodePayload payload = new InviteCodePayload(45L, RelationshipType.FAMILY, (short) 1, 1L);
        CareTarget ct = careTarget(45L);
        Instant expiresAt = Instant.now().plusSeconds(3600);

        given(inviteCodeStore.find("AB3CD7EF")).willReturn(Optional.of(payload));
        given(careTargetRepository.findById(45L)).willReturn(Optional.of(ct));
        given(userRepository.findById(1L)).willReturn(Optional.empty());
        given(inviteCodeStore.expiresAt("AB3CD7EF")).willReturn(Optional.of(expiresAt));

        InvitePreviewResponse resp = relationshipService.previewInviteCode(7L, "AB3CD7EF");

        assertThat(resp.careTargetName()).isEqualTo("박순자");
        assertThat(resp.inviterName()).isNull();
        assertThat(resp.expiresAt()).isEqualTo(expiresAt);
    }

    // ── acceptInviteCode (R1-b) ────────────────────────────────────────────

    @Test
    @DisplayName("acceptInviteCode: 유효 코드 수락 → is_primary=false로 관계 저장")
    void acceptInviteCode_success() {
        InviteCodePayload payload = new InviteCodePayload(45L, RelationshipType.FAMILY, (short) 2, 1L);
        CareTarget ct = careTarget(45L);

        given(userRepository.findById(2L)).willReturn(Optional.of(user(2L, "이보호")));
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
        then(careRelationshipRepository).should().save(argThat(cr ->
                cr.getUserId() == 2L
                        && cr.getCareTarget().getId() == 45L
                        && cr.getRelationshipType() == RelationshipType.FAMILY
                        && cr.getNotifyPriority() == (short) 2
                        && !cr.isPrimary()));
    }

    @Test
    @DisplayName("acceptInviteCode: 만료·사용된 코드 → 404 INVALID_INVITE_CODE + 프로빙 1회 기록")
    void acceptInviteCode_invalidCode() {
        given(userRepository.findById(2L)).willReturn(Optional.of(user(2L, "이보호")));
        given(inviteCodeStore.findAndDelete("BADCODE0")).willReturn(Optional.empty());

        assertThatThrownBy(() -> relationshipService.acceptInviteCode(2L, "BADCODE0"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RelationshipErrorCode.INVALID_INVITE_CODE);

        then(inviteCodeProbeThrottle).should().recordFailure(2L);
    }

    @Test
    @DisplayName("acceptInviteCode: 프로빙 제한 초과 → 429, 코드를 소모하지 않는다")
    void acceptInviteCode_throttled_codeNotConsumed() {
        // 미리보기만 막으면 방어가 성립하지 않는다 — 여기를 맞히면 그 자리에서 보호자가 된다.
        // 공격자가 이름만 얻는 쪽을 쓸 이유가 없다
        given(userRepository.findById(2L)).willReturn(Optional.of(user(2L, "이보호")));
        given(inviteCodeProbeThrottle.isBlocked(2L)).willReturn(true);

        assertThatThrownBy(() -> relationshipService.acceptInviteCode(2L, "AB3CD7EF"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RelationshipErrorCode.TOO_MANY_INVITE_ATTEMPTS);

        then(inviteCodeStore).should(never()).findAndDelete(any());
    }

    @Test
    @DisplayName("acceptInviteCode: 이미 보호자여서 실패해도 프로빙으로 세지 않는다")
    void acceptInviteCode_alreadyGuardian_isNotProbing() {
        // 코드를 맞힌 사람이다. 링크를 두 번 눌렀다고 다음 초대까지 막히면 안 된다
        InviteCodePayload payload = new InviteCodePayload(45L, RelationshipType.FAMILY, (short) 2, 1L);

        given(userRepository.findById(2L)).willReturn(Optional.of(user(2L, "이보호")));
        given(inviteCodeStore.findAndDelete("AB3CD7EF")).willReturn(Optional.of(payload));
        given(careTargetRepository.findById(45L)).willReturn(Optional.of(careTarget(45L)));
        given(careRelationshipRepository.existsByUserIdAndCareTargetId(2L, 45L)).willReturn(true);

        assertThatThrownBy(() -> relationshipService.acceptInviteCode(2L, "AB3CD7EF"))
                .isInstanceOf(BusinessException.class);

        then(inviteCodeProbeThrottle).should(never()).recordFailure(any());
        then(inviteCodeProbeThrottle).should().reset(2L);
    }

    @Test
    @DisplayName("acceptInviteCode: 이미 보호자인 경우 → 409 RELATIONSHIP_ALREADY_EXISTS")
    void acceptInviteCode_alreadyGuardian() {
        InviteCodePayload payload = new InviteCodePayload(45L, RelationshipType.FAMILY, (short) 2, 1L);
        CareTarget ct = careTarget(45L);

        given(userRepository.findById(2L)).willReturn(Optional.of(user(2L, "이보호")));
        given(inviteCodeStore.findAndDelete("AB3CD7EF")).willReturn(Optional.of(payload));
        given(careTargetRepository.findById(45L)).willReturn(Optional.of(ct));
        given(careRelationshipRepository.existsByUserIdAndCareTargetId(2L, 45L)).willReturn(true);

        assertThatThrownBy(() -> relationshipService.acceptInviteCode(2L, "AB3CD7EF"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RelationshipErrorCode.RELATIONSHIP_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("acceptInviteCode: 노인 계정(CARE_RECIPIENT) → 403 ACCESS_DENIED, 코드 미소모")
    void acceptInviteCode_careRecipient_blocked_codeNotConsumed() {
        User recipient = User.create("old@b.com", "hashed", "박순자", Role.CARE_RECIPIENT);
        ReflectionTestUtils.setField(recipient, "id", 9L);
        given(userRepository.findById(9L)).willReturn(Optional.of(recipient));

        assertThatThrownBy(() -> relationshipService.acceptInviteCode(9L, "AB3CD7EF"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.ACCESS_DENIED);
        then(inviteCodeStore).should(never()).findAndDelete(any());
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

        given(accessValidator.getRelationshipOrThrow(1L, 45L)).willReturn(cr1);
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
        given(accessValidator.getRelationshipOrThrow(1L, 45L))
                .willThrow(new BusinessException(CommonErrorCode.ACCESS_DENIED));

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

        given(careRelationshipRepository.findById(7L)).willReturn(Optional.of(target));

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

        given(careRelationshipRepository.findById(7L)).willReturn(Optional.of(target));
        willThrow(new BusinessException(CommonErrorCode.ACCESS_DENIED))
                .given(accessValidator).requirePrimary(1L, 45L);

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

        given(careRelationshipRepository.findById(7L)).willReturn(Optional.of(target));

        relationshipService.deleteRelationship(1L, 7L);

        then(careRelationshipRepository).should().delete(target);
    }

    @Test
    @DisplayName("deleteRelationship: 주 보호자 해제 시도 → 409 CANNOT_DELETE_PRIMARY")
    void deleteRelationship_cannotDeletePrimary() {
        CareTarget ct = careTarget(45L);
        CareRelationship target = CareRelationship.of(1L, ct, RelationshipType.FAMILY, true, (short) 1);
        ReflectionTestUtils.setField(target, "id", 1L);

        given(careRelationshipRepository.findById(1L)).willReturn(Optional.of(target));

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

        given(careRelationshipRepository.findById(7L)).willReturn(Optional.of(target));
        willThrow(new BusinessException(CommonErrorCode.ACCESS_DENIED))
                .given(accessValidator).requirePrimary(1L, 45L);

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

}
