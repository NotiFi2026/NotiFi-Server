package com.notifi.server.domain.caretarget;

import com.notifi.server.domain.caretarget.dto.CareTargetCreateRequest;
import com.notifi.server.domain.caretarget.dto.CareTargetCreateResponse;
import com.notifi.server.domain.caretarget.dto.CareTargetSummaryResponse;
import com.notifi.server.domain.user.Role;
import com.notifi.server.domain.user.User;
import com.notifi.server.domain.user.UserRepository;
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
        // CareRelationship이 isPrimary=true로 저장되는지 검증
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

    // ── argThat helper ────────────────────────────────────────────────────

    private static <T> T argThat(org.mockito.ArgumentMatcher<T> matcher) {
        return org.mockito.ArgumentMatchers.argThat(matcher);
    }
}
