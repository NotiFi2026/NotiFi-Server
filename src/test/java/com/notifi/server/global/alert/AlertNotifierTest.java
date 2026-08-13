package com.notifi.server.global.alert;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 실제 HTTP 발송 경로를 태운다 (JDK 내장 서버 — 의존성 추가 없이).
 *
 * 알림은 응급 발송 실패 시에만 불리는 안전망이라, <b>알림 자체가 장애를 만들면 안 된다.</b>
 * webhook이 죽어 있거나 느려도 호출자가 깨지지 않는 것이 여기서 지켜야 할 성질이다.
 */
class AlertNotifierTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    /** 요청 바디를 받아 두는 로컬 webhook. status로 응답 코드를 정한다. */
    private String startServer(int status, AtomicReference<String> received,
                               CountDownLatch arrived, AtomicInteger hitCount) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            hitCount.incrementAndGet();
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
            arrived.countDown();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/hook";
    }

    @Test
    @DisplayName("webhook 설정 시 제목과 컨텍스트가 담겨 전송된다")
    void sendsTitleAndContext() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        CountDownLatch arrived = new CountDownLatch(1);
        String url = startServer(204, body, arrived, new AtomicInteger());

        AlertNotifier notifier = new AlertNotifier(url, "prod");
        notifier.critical("응급 알림 도달 실패", Map.of("care_target_id", 45));

        assertThat(arrived.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(body.get()).contains("응급 알림 도달 실패", "care_target_id=45", "prod");
        notifier.shutdown();
    }

    @Test
    @DisplayName("webhook 미설정이면 아무것도 보내지 않는다 — 로컬 개발이 막히면 안 된다")
    void disabledWhenUrlBlank() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        CountDownLatch arrived = new CountDownLatch(1);
        startServer(204, new AtomicReference<>(), arrived, hits);

        AlertNotifier notifier = new AlertNotifier("", "local");
        notifier.critical("보내면 안 되는 알림", Map.of());

        assertThat(arrived.await(1, TimeUnit.SECONDS)).isFalse();
        assertThat(hits.get()).isZero();
        notifier.shutdown();
    }

    @Test
    @DisplayName("webhook이 실패해도 호출자에게 예외가 나가지 않는다")
    void swallowsWebhookFailure() throws Exception {
        CountDownLatch arrived = new CountDownLatch(1);
        String url = startServer(500, new AtomicReference<>(), arrived, new AtomicInteger());

        AlertNotifier notifier = new AlertNotifier(url, "prod");

        // 알림 전송 실패가 응급 발송 로직으로 번지면, 알림을 붙인 것이 오히려 장애가 된다
        assertThatCode(() -> notifier.critical("실패할 알림", Map.of())).doesNotThrowAnyException();
        assertThat(arrived.await(5, TimeUnit.SECONDS)).isTrue();
        notifier.shutdown();
    }

    @Test
    @DisplayName("webhook이 느려도 호출자는 즉시 반환된다 — 응급 경로를 붙잡지 않는다")
    void doesNotBlockCallerOnSlowWebhook() throws Exception {
        CountDownLatch arrived = new CountDownLatch(1);
        // 실제로 응답을 늦추는 서버여야 비동기성이 검증된다.
        // 도달 불가 주소는 네트워크가 연결을 즉시 거절하면 동기 구현으로도 통과한다.
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            arrived.countDown();
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/hook";

        AlertNotifier notifier = new AlertNotifier(url, "prod");

        long startMs = System.currentTimeMillis();
        notifier.critical("느린 webhook", Map.of());
        long elapsedMs = System.currentTimeMillis() - startMs;

        // 전송은 전용 스레드로 넘어가므로 서버가 2초를 끌어도 호출은 곧바로 끝난다
        assertThat(elapsedMs).isLessThan(500);
        assertThat(arrived.await(5, TimeUnit.SECONDS)).isTrue();
        notifier.shutdown();
    }

    @Test
    @DisplayName("종료된 뒤 호출해도 예외가 나가지 않는다 — 앱 종료 중 응급 실패가 번지면 안 된다")
    void doesNotThrowAfterShutdown() throws Exception {
        String url = startServer(204, new AtomicReference<>(), new CountDownLatch(1), new AtomicInteger());
        AlertNotifier notifier = new AlertNotifier(url, "prod");
        notifier.shutdown();

        // 무제한 큐(Executors.newSingleThreadExecutor)였다면 여기서
        // RejectedExecutionException이 호출자로 올라간다
        assertThatCode(() -> notifier.critical("종료 후 알림", Map.of("care_target_id", 45)))
                .doesNotThrowAnyException();
    }
}
