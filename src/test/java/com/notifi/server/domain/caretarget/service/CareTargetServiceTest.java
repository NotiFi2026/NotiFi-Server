package com.notifi.server.domain.caretarget.service;

import com.notifi.server.domain.caretarget.dto.CareTargetCreateRequest;
import com.notifi.server.domain.caretarget.dto.CareTargetCreateResponse;
import com.notifi.server.domain.caretarget.dto.CareTargetDetailResponse;
import com.notifi.server.domain.caretarget.dto.CareTargetSummaryResponse;
import com.notifi.server.domain.caretarget.dto.CareTargetUpdateRequest;
import com.notifi.server.domain.caretarget.entity.CareRelationship;
import com.notifi.server.domain.caretarget.entity.CareTarget;
import com.notifi.server.domain.caretarget.entity.Gender;
import com.notifi.server.domain.caretarget.entity.RelationshipType;
import com.notifi.server.domain.caretarget.exception.CareTargetErrorCode;
import com.notifi.server.domain.caretarget.repository.CareRelationshipRepository;
import com.notifi.server.domain.caretarget.repository.CareTargetRepository;
import com.notifi.server.domain.user.entity.Role;
import com.notifi.server.domain.user.entity.User;
import com.notifi.server.domain.user.repository.UserRepository;
import com.notifi.server.global.exception.BusinessException;
import com.notifi.server.global.exception.CommonErrorCode;
import com.notifi.server.global.response.PageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class CareTargetServiceTest {

    @Mock CareTargetRepository careTargetRepository;
    @Mock CareRelationshipRepository careRelationshipRepository;
    @Mock UserRepository userRepository;

    @InjectMocks CareTargetService careTargetService;

    // ── register (C1) ─────────────────────────────────────────────────────

    @Test
    @DisplayName("register: 노인 등록 후 care_target_id 반환 + 주 보호자 관계 저장")
    void register_success_guardian() {
        User user = User.create("a@b.com", "hashed", "김보호", Role.GUARDIAN);
        ReflectionTestUtils.setField(user, "id", 1L);

        CareTarget saved = CareTarget.create("박순자", null, Gender.FEMALE, null, null);
        ReflectionTestUtils.setField(saved, "id", 45L);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(careTargetRepository.save(any(CareTarget.class))).willReturn(saved);
        given(careRelationshipRepository.save(any(CareRelationship.class))).willAnswer(i -> i.getArgument(0));

        CareTargetCreateRequest req = new CareTargetCreateRequest("박순자", null, Gender.FEMALE, null, null);
        CareTargetCreateResponse resp = careTargetService.register(1L, req);

        assertThat(resp.careTargetId()).isEqualTo(45L);
        then(careRelationshipRepository).should().save(
                argThat(cr -> cr.isPrimary() && cr.getRelationshipType() == RelationshipType.FAMILY)
        );
    }

    @Test
    @DisplayName("register: SOCIAL_WORKER 역할이면 SOCIAL_WORKER 관계 타입으로 등록")
    void register_socialWorker_relationshipType() {
        User user = User.create("sw@b.com", "hashed", "이복지", Role.SOCIAL_WORKER);
        ReflectionTestUtils.setField(user, "id", 2L);

        CareTarget saved = CareTarget.create("박순자", null, null, null, null);
        ReflectionTestUtils.setField(saved, "id", 46L);

        given(userRepository.findById(2L)).willReturn(Optional.of(user));
        given(careTargetRepository.save(any(CareTarget.class))).willReturn(saved);
        given(careRelationshipRepository.save(any(CareRelationship.class))).willAnswer(i -> i.getArgument(0));

        careTargetService.register(2L, new CareTargetCreateRequest("박순자", null, null, null, null));

        then(careRelationshipRepository).should().save(
                argThat(cr -> cr.getRelationshipType() == RelationshipType.SOCIAL_WORKER)
        );
    }

    // ── getMyCareTargets (C2) ─────────────────────────────────────────────

    @Test
    @DisplayName("getMyCareTargets: 보호자가 등록한 노인 목록 반환 + isPrimary 반영")
    void getMyCareTargets_returnsSummaries() {
        CareTarget ct = CareTarget.create("박순자", null, Gender.FEMALE, null, null);
        ReflectionTestUtils.setField(ct, "id", 45L);

        CareRelationship cr = CareRelationship.of(1L, ct, RelationshipType.FAMILY, true, (short) 1);

        PageRequest pageable = PageRequest.of(0, 20);
        given(careRelationshipRepository.findByUserIdWithCareTarget(1L, pageable))
                .willReturn(new PageImpl<>(List.of(cr), pageable, 1));

        PageResponse<CareTargetSummaryResponse> result = careTargetService.getMyCareTargets(1L, pageable);

        assertThat(result.content()).hasSize(1);
        CareTargetSummaryResponse summary = result.content().get(0);
        assertThat(summary.careTargetId()).isEqualTo(45L);
        assertThat(summary.name()).isEqualTo("박순자");
        assertThat(summary.isPrimary()).isTrue();
        assertThat(summary.currentRiskLevel()).isNull();
        assertThat(summary.deviceCount()).isZero();
        assertThat(result.totalElements()).isEqualTo(1);
    }

    // ── getDetail (C3) ────────────────────────────────────────────────────

    @Test
    @DisplayName("getDetail: 관계 있는 노인 상세 정보 반환")
    void getDetail_success() {
        CareTarget ct = CareTarget.create("박순자", null, Gender.FEMALE, "서울시 강남구", "당뇨 약 복용");
        ReflectionTestUtils.setField(ct, "id", 45L);

        CareRelationship cr = CareRelationship.of(1L, ct, RelationshipType.FAMILY, true, (short) 1);
        given(careRelationshipRepository.findByUserIdAndCareTargetId(1L, 45L)).willReturn(Optional.of(cr));

        CareTargetDetailResponse result = careTargetService.getDetail(1L, 45L);

        assertThat(result.careTargetId()).isEqualTo(45L);
        assertThat(result.name()).isEqualTo("박순자");
        assertThat(result.gender()).isEqualTo(Gender.FEMALE);
        assertThat(result.address()).isEqualTo("서울시 강남구");
        assertThat(result.isPrimary()).isTrue();
    }

    @Test
    @DisplayName("getDetail: 관계 없고 노인 존재 → 403 ACCESS_DENIED")
    void getDetail_noRelationship_targetExists_accessDenied() {
        given(careRelationshipRepository.findByUserIdAndCareTargetId(1L, 45L)).willReturn(Optional.empty());
        given(careTargetRepository.existsById(45L)).willReturn(true);

        assertThatThrownBy(() -> careTargetService.getDetail(1L, 45L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("getDetail: 노인 없음(soft-deleted 포함) → 404 CARE_TARGET_NOT_FOUND")
    void getDetail_targetNotFound() {
        given(careRelationshipRepository.findByUserIdAndCareTargetId(1L, 99L)).willReturn(Optional.empty());
        given(careTargetRepository.existsById(99L)).willReturn(false);

        assertThatThrownBy(() -> careTargetService.getDetail(1L, 99L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CareTargetErrorCode.CARE_TARGET_NOT_FOUND);
    }

    // ── update (C4) ───────────────────────────────────────────────────────

    @Test
    @DisplayName("update: name만 변경하면 나머지 필드는 유지")
    void update_partialName() {
        CareTarget ct = CareTarget.create("박순자", null, Gender.FEMALE, "서울시 강남구", null);
        ReflectionTestUtils.setField(ct, "id", 45L);

        CareRelationship cr = CareRelationship.of(1L, ct, RelationshipType.FAMILY, true, (short) 1);
        given(careRelationshipRepository.findByUserIdAndCareTargetId(1L, 45L)).willReturn(Optional.of(cr));

        CareTargetUpdateRequest req = new CareTargetUpdateRequest("박순이", null, null, null, null);
        CareTargetDetailResponse result = careTargetService.update(1L, 45L, req);

        assertThat(result.name()).isEqualTo("박순이");
        assertThat(result.gender()).isEqualTo(Gender.FEMALE);     // 유지
        assertThat(result.address()).isEqualTo("서울시 강남구");    // 유지
    }

    // ── delete (C5) ───────────────────────────────────────────────────────

    @Test
    @DisplayName("delete: 주 보호자가 삭제하면 deletedAt 설정")
    void delete_primaryGuardian_softDeletes() {
        CareTarget ct = CareTarget.create("박순자", null, Gender.FEMALE, null, null);
        ReflectionTestUtils.setField(ct, "id", 45L);

        CareRelationship cr = CareRelationship.of(1L, ct, RelationshipType.FAMILY, true, (short) 1);
        given(careRelationshipRepository.findByUserIdAndCareTargetId(1L, 45L)).willReturn(Optional.of(cr));

        careTargetService.delete(1L, 45L);

        assertThat(ct.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("delete: 주 보호자 아닌 경우 → 403 ACCESS_DENIED")
    void delete_nonPrimary_accessDenied() {
        CareTarget ct = CareTarget.create("박순자", null, Gender.FEMALE, null, null);
        ReflectionTestUtils.setField(ct, "id", 45L);

        CareRelationship cr = CareRelationship.of(2L, ct, RelationshipType.FAMILY, false, (short) 2);
        given(careRelationshipRepository.findByUserIdAndCareTargetId(2L, 45L)).willReturn(Optional.of(cr));

        assertThatThrownBy(() -> careTargetService.delete(2L, 45L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.ACCESS_DENIED);
    }

    // ── argThat helper ────────────────────────────────────────────────────

    private static <T> T argThat(org.mockito.ArgumentMatcher<T> matcher) {
        return org.mockito.ArgumentMatchers.argThat(matcher);
    }
}
