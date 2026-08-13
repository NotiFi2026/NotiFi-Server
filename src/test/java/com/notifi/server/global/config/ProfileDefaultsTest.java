package com.notifi.server.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 프로파일별 기본값을 고정한다.
 *
 * <p>여기 걸린 값들은 <b>환경변수를 주지 않았을 때 어떻게 동작하느냐</b>가 곧 계약인 것들이다.
 * 로컬에서는 아무 증상이 없고 배포한 뒤에야 드러나므로, 눈으로 확인하는 것으로는 회귀를 못 막는다.
 *
 * <p>애플리케이션 컨텍스트를 띄우지 않고 설정만 읽는다 — DB·Redis가 필요 없다.
 */
class ProfileDefaultsTest {

    @Configuration
    static class Empty {
    }

    private Environment loadEnvironment(String profile) {
        SpringApplication app = new SpringApplicationBuilder(Empty.class)
                .web(WebApplicationType.NONE)
                .profiles(profile)
                // .env 파일이 있으면 개발자 로컬 값이 섞여 테스트가 환경에 따라 흔들린다
                .properties("spring.config.import=")
                .build();
        try (ConfigurableApplicationContext context = app.run()) {
            return context.getEnvironment();
        }
    }

    @Test
    @DisplayName("prod: Swagger는 기본 차단 — 인증 없이 API 구조가 공개되면 안 된다")
    void prodDisablesSwaggerByDefault() {
        Environment env = loadEnvironment("prod");

        assertThat(env.getProperty("springdoc.api-docs.enabled")).isEqualTo("false");
        assertThat(env.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("false");
    }

    @Test
    @DisplayName("local: Swagger는 기본 활성 — 개발·연동 확인에 쓴다")
    void localEnablesSwaggerByDefault() {
        Environment env = loadEnvironment("local");

        assertThat(env.getProperty("springdoc.api-docs.enabled")).isEqualTo("true");
    }

    @Test
    @DisplayName("prod: 알림 환경명이 prod — [local]로 도착하면 어디서 난 장애인지 오판한다")
    void prodLabelsAlertsAsProd() {
        Environment env = loadEnvironment("prod");

        assertThat(env.getProperty("alert.environment")).isEqualTo("prod");
    }

    @Test
    @DisplayName("prod: 로그는 ECS JSON — 수집기가 파싱할 수 있어야 한다")
    void prodEmitsStructuredLogs() {
        Environment env = loadEnvironment("prod");

        assertThat(env.getProperty("logging.structured.format.console")).isEqualTo("ecs");
    }

    @Test
    @DisplayName("알림 webhook은 기본 미설정 — 로컬 개발이 외부 호출로 막히면 안 된다")
    void alertWebhookIsOptional() {
        assertThat(loadEnvironment("local").getProperty("alert.webhook-url")).isEmpty();
        assertThat(loadEnvironment("prod").getProperty("alert.webhook-url")).isEmpty();
    }
}
