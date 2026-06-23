package com.notifi.server;

import com.notifi.server.domain.user.UserRepository;
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
    @MockitoBean StringRedisTemplate stringRedisTemplate;

    @Test
    void contextLoads() {
    }
}
