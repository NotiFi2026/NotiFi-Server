package com.notifi.server;

import com.notifi.server.domain.caretarget.repository.CareRelationshipRepository;
import com.notifi.server.domain.caretarget.repository.CareTargetRepository;
import com.notifi.server.domain.device.repository.DeviceRepository;
import com.notifi.server.domain.escalation.repository.EscalationRepository;
import com.notifi.server.domain.escalation.repository.EscalationStepRepository;
import com.notifi.server.domain.notification.repository.FcmTokenRepository;
import com.notifi.server.domain.notification.repository.NotificationRepository;
import com.notifi.server.domain.sensing.repository.PoseClipRepository;
import com.notifi.server.domain.sensing.repository.RiskAssessmentRepository;
import com.notifi.server.domain.sensing.repository.SensingEventRepository;
import com.notifi.server.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class NotiFiServerApplicationTests {

    // 테스트 프로파일은 DataSource/JPA/Redis auto-config를 제외 → 인프라 빈만 Mock으로 대체
    @MockitoBean UserRepository userRepository;
    @MockitoBean CareTargetRepository careTargetRepository;
    @MockitoBean CareRelationshipRepository careRelationshipRepository;
    @MockitoBean StringRedisTemplate stringRedisTemplate;
    @MockitoBean DeviceRepository deviceRepository;
    @MockitoBean SensingEventRepository sensingEventRepository;
    @MockitoBean RiskAssessmentRepository riskAssessmentRepository;
    @MockitoBean EscalationRepository escalationRepository;
    @MockitoBean EscalationStepRepository escalationStepRepository;
    @MockitoBean FcmTokenRepository fcmTokenRepository;
    @MockitoBean NotificationRepository notificationRepository;
    @MockitoBean PoseClipRepository poseClipRepository;

    @Test
    void contextLoads() {
    }
}
