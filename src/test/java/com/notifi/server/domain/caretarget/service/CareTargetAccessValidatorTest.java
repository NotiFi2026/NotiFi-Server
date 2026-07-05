package com.notifi.server.domain.caretarget.service;

import com.notifi.server.domain.caretarget.entity.CareRelationship;
import com.notifi.server.domain.caretarget.entity.CareTarget;
import com.notifi.server.domain.caretarget.entity.Gender;
import com.notifi.server.domain.caretarget.entity.RelationshipType;
import com.notifi.server.domain.caretarget.exception.CareTargetErrorCode;
import com.notifi.server.domain.caretarget.repository.CareRelationshipRepository;
import com.notifi.server.domain.caretarget.repository.CareTargetRepository;
import com.notifi.server.global.exception.BusinessException;
import com.notifi.server.global.exception.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class CareTargetAccessValidatorTest {

    @Mock CareRelationshipRepository careRelationshipRepository;
    @Mock CareTargetRepository careTargetRepository;

    @InjectMocks CareTargetAccessValidator validator;

    // ── requireRelationship ───────────────────────────────────────────────

    @Test
    @DisplayName("requireRelationship: 관계 있으면 통과")
    void requireRelationship_related_passes() {
        given(careRelationshipRepository.existsByUserIdAndCareTargetId(1L, 45L)).willReturn(true);

        assertThatCode(() -> validator.requireRelationship(1L, 45L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("requireRelationship: 관계 없고 노인 존재 → 403 ACCESS_DENIED")
    void requireRelationship_notRelated_targetExists_accessDenied() {
        given(careRelationshipRepository.existsByUserIdAndCareTargetId(1L, 45L)).willReturn(false);
        given(careTargetRepository.existsById(45L)).willReturn(true);

        assertThatThrownBy(() -> validator.requireRelationship(1L, 45L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("requireRelationship: 노인 없음 → 404 CARE_TARGET_NOT_FOUND")
    void requireRelationship_targetNotFound() {
        given(careRelationshipRepository.existsByUserIdAndCareTargetId(1L, 99L)).willReturn(false);
        given(careTargetRepository.existsById(99L)).willReturn(false);

        assertThatThrownBy(() -> validator.requireRelationship(1L, 99L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CareTargetErrorCode.CARE_TARGET_NOT_FOUND);
    }

    // ── getRelationshipOrThrow ────────────────────────────────────────────

    @Test
    @DisplayName("getRelationshipOrThrow: 관계 있으면 관계 엔티티 반환")
    void getRelationshipOrThrow_related_returnsRelationship() {
        CareRelationship cr = relationship(1L, 45L, true);
        given(careRelationshipRepository.findByUserIdAndCareTargetId(1L, 45L)).willReturn(Optional.of(cr));

        assertThat(validator.getRelationshipOrThrow(1L, 45L)).isSameAs(cr);
    }

    @Test
    @DisplayName("getRelationshipOrThrow: 관계 없고 노인 존재 → 403 ACCESS_DENIED")
    void getRelationshipOrThrow_notRelated_accessDenied() {
        given(careRelationshipRepository.findByUserIdAndCareTargetId(1L, 45L)).willReturn(Optional.empty());
        given(careTargetRepository.existsById(45L)).willReturn(true);

        assertThatThrownBy(() -> validator.getRelationshipOrThrow(1L, 45L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("getRelationshipOrThrow: 노인 없음 → 404 CARE_TARGET_NOT_FOUND")
    void getRelationshipOrThrow_targetNotFound() {
        given(careRelationshipRepository.findByUserIdAndCareTargetId(1L, 99L)).willReturn(Optional.empty());
        given(careTargetRepository.existsById(99L)).willReturn(false);

        assertThatThrownBy(() -> validator.getRelationshipOrThrow(1L, 99L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CareTargetErrorCode.CARE_TARGET_NOT_FOUND);
    }

    // ── requirePrimary ────────────────────────────────────────────────────

    @Test
    @DisplayName("requirePrimary: 주 보호자면 통과")
    void requirePrimary_primary_passes() {
        CareRelationship cr = relationship(1L, 45L, true);
        given(careRelationshipRepository.findByUserIdAndCareTargetId(1L, 45L)).willReturn(Optional.of(cr));

        assertThatCode(() -> validator.requirePrimary(1L, 45L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("requirePrimary: 주 보호자 아님 → 403 ACCESS_DENIED")
    void requirePrimary_nonPrimary_accessDenied() {
        CareRelationship cr = relationship(2L, 45L, false);
        given(careRelationshipRepository.findByUserIdAndCareTargetId(2L, 45L)).willReturn(Optional.of(cr));

        assertThatThrownBy(() -> validator.requirePrimary(2L, 45L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.ACCESS_DENIED);
    }

    // ── requireRelationshipOrSelf ─────────────────────────────────────────

    @Test
    @DisplayName("requireRelationshipOrSelf: 보호자 관계 있으면 통과 (노인 조회 없음)")
    void requireRelationshipOrSelf_related_passes() {
        given(careRelationshipRepository.existsByUserIdAndCareTargetId(1L, 45L)).willReturn(true);

        assertThatCode(() -> validator.requireRelationshipOrSelf(1L, 45L)).doesNotThrowAnyException();
        then(careTargetRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("requireRelationshipOrSelf: 노인 본인 계정이면 통과")
    void requireRelationshipOrSelf_self_passes() {
        given(careRelationshipRepository.existsByUserIdAndCareTargetId(9L, 45L)).willReturn(false);
        given(careTargetRepository.findById(45L)).willReturn(Optional.of(linkedCareTarget(45L, 9L)));

        assertThatCode(() -> validator.requireRelationshipOrSelf(9L, 45L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("requireRelationshipOrSelf: 관계도 본인도 아님 → 403 ACCESS_DENIED")
    void requireRelationshipOrSelf_thirdParty_accessDenied() {
        given(careRelationshipRepository.existsByUserIdAndCareTargetId(3L, 45L)).willReturn(false);
        given(careTargetRepository.findById(45L)).willReturn(Optional.of(linkedCareTarget(45L, 9L)));

        assertThatThrownBy(() -> validator.requireRelationshipOrSelf(3L, 45L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("requireRelationshipOrSelf: 노인 없음 → 404 CARE_TARGET_NOT_FOUND")
    void requireRelationshipOrSelf_targetNotFound() {
        given(careRelationshipRepository.existsByUserIdAndCareTargetId(9L, 99L)).willReturn(false);
        given(careTargetRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> validator.requireRelationshipOrSelf(9L, 99L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CareTargetErrorCode.CARE_TARGET_NOT_FOUND);
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private CareRelationship relationship(Long userId, Long careTargetId, boolean isPrimary) {
        CareTarget ct = CareTarget.create("박순자", null, Gender.FEMALE, null, null);
        ReflectionTestUtils.setField(ct, "id", careTargetId);
        return CareRelationship.of(userId, ct, RelationshipType.FAMILY, isPrimary, (short) 1);
    }

    private CareTarget linkedCareTarget(Long careTargetId, Long linkedUserId) {
        CareTarget ct = CareTarget.create("박순자", null, Gender.FEMALE, null, null);
        ReflectionTestUtils.setField(ct, "id", careTargetId);
        ct.linkUser(linkedUserId);
        return ct;
    }
}
