package com.notifi.server.domain.escalation.service;

import com.notifi.server.domain.caretarget.entity.CareTarget;
import com.notifi.server.domain.caretarget.repository.CareTargetRepository;
import com.notifi.server.domain.caretarget.service.CareTargetAccessValidator;
import com.notifi.server.domain.escalation.dto.EscalationDetailResponse;
import com.notifi.server.domain.escalation.entity.Escalation;
import com.notifi.server.domain.escalation.entity.EscalationStatus;
import com.notifi.server.domain.escalation.entity.ResolutionType;
import com.notifi.server.domain.escalation.exception.EscalationErrorCode;
import com.notifi.server.domain.escalation.repository.EscalationRepository;
import com.notifi.server.domain.escalation.repository.EscalationStepRepository;
import com.notifi.server.domain.sensing.entity.EventType;
import com.notifi.server.domain.sensing.entity.RiskAssessment;
import com.notifi.server.domain.sensing.entity.RiskLevel;
import com.notifi.server.domain.sensing.entity.SensingEvent;
import com.notifi.server.domain.sensing.repository.RiskAssessmentRepository;
import com.notifi.server.domain.sensing.repository.SensingEventRepository;
import com.notifi.server.global.exception.BusinessException;
import com.notifi.server.global.exception.CommonErrorCode;
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
import static org.mockito.BDDMockito.willThrow;

/**
 * E4 — 노인 본인 "괜찮아요".
 *
 * <p>음성 확인이 어려울 때(말을 못 하거나 주변이 시끄럽거나)의 유일한 대안 경로다.
 * 여기가 막히면 오탐이 그대로 119까지 올라간다.
 */
@ExtendWith(MockitoExtension.class)
class EscalationSelfResponseTest {

    @Mock EscalationRepository escalationRepository;
    @Mock EscalationStepRepository escalationStepRepository;
    @Mock RiskAssessmentRepository riskAssessmentRepository;
    @Mock SensingEventRepository sensingEventRepository;
    @Mock CareTargetRepository careTargetRepository;
    @Mock CareTargetAccessValidator accessValidator;

    @InjectMocks EscalationService escalationService;

    private static final Long RECIPIENT_USER_ID = 9L;
    private static final Long CARE_TARGET_ID = 45L;
    private static final Long ESCALATION_ID = 31L;

    private Escalation givenInProgressEscalation() {
        Escalation escalation = Escalation.start(2L);
        ReflectionTestUtils.setField(escalation, "id", ESCALATION_ID);

        SensingEvent event = SensingEvent.create(
                CARE_TARGET_ID, null, EventType.FALL, null, null, null, null, null,
                "v1", null, Instant.parse("2026-08-13T03:22:00Z"));
        ReflectionTestUtils.setField(event, "id", 7L);

        RiskAssessment ra = RiskAssessment.of(7L, (short) 90, RiskLevel.DANGER, null, "v1",
                Instant.parse("2026-08-13T03:22:00Z"));

        given(escalationRepository.findByIdForUpdate(ESCALATION_ID)).willReturn(Optional.of(escalation));
        given(riskAssessmentRepository.findById(any())).willReturn(Optional.of(ra));
        given(sensingEventRepository.findById(any())).willReturn(Optional.of(event));
        return escalation;
    }

    @Test
    @DisplayName("노인이 응답하면 음성 USER_OK와 같은 SELF_RESOLVED로 해소된다")
    void selfConfirm_resolvesAsSelfResolved() {
        Escalation escalation = givenInProgressEscalation();
        given(escalationStepRepository.findByEscalationIdOrderByStepOrderAsc(ESCALATION_ID))
                .willReturn(List.of());
        given(careTargetRepository.findById(CARE_TARGET_ID))
                .willReturn(Optional.of(CareTarget.create("박순자", null, null, null, null)));

        EscalationDetailResponse res =
                escalationService.selfConfirmSafe(RECIPIENT_USER_ID, ESCALATION_ID);

        // 음성으로 답했든 버튼을 눌렀든 "노인이 스스로 괜찮다고 했다"는 한 가지로 읽혀야 한다
        assertThat(escalation.getStatus()).isEqualTo(EscalationStatus.RESOLVED);
        assertThat(escalation.getResolutionType()).isEqualTo(ResolutionType.SELF_RESOLVED);
        assertThat(res.escalationId()).isEqualTo(ESCALATION_ID);
    }

    @Test
    @DisplayName("보호자는 이 경로를 쓸 수 없다 — 대신 눌러 주면 자기응답의 의미가 사라진다")
    void selfConfirm_guardianBlocked() {
        givenInProgressEscalation();
        willThrow(new BusinessException(CommonErrorCode.ACCESS_DENIED))
                .given(accessValidator).requireSelf(1L, CARE_TARGET_ID);

        assertThatThrownBy(() -> escalationService.selfConfirmSafe(1L, ESCALATION_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(CommonErrorCode.ACCESS_DENIED));
    }

    @Test
    @DisplayName("이미 해소된 에스컬레이션은 409 — 음성 응답과 버튼이 겹쳐도 한쪽만 이긴다")
    void selfConfirm_alreadyResolved_conflicts() {
        Escalation escalation = givenInProgressEscalation();
        escalation.resolve(ResolutionType.SELF_RESOLVED, "음성 확인 USER_OK 응답 자동 해소");

        assertThatThrownBy(() -> escalationService.selfConfirmSafe(RECIPIENT_USER_ID, ESCALATION_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(EscalationErrorCode.ESCALATION_ALREADY_RESOLVED));
    }

    @Test
    @DisplayName("없는 에스컬레이션은 404")
    void selfConfirm_notFound() {
        given(escalationRepository.findByIdForUpdate(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> escalationService.selfConfirmSafe(RECIPIENT_USER_ID, 999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(EscalationErrorCode.ESCALATION_NOT_FOUND));
    }
}
