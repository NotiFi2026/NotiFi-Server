package com.notifi.server.global.alert;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 치명 실패를 사람에게 알린다 (Discord·Slack 등 webhook).
 *
 * <p>로그만으로는 부족한 실패가 있다 — 낙상이 감지되고 에스컬레이션까지 돌았는데
 * 보호자에게 푸시가 안 갔다면, 그건 로그에 WARN 한 줄로 남고 아무도 모른 채 지나간다.
 * 그런 것만 여기로 보낸다. 흔한 실패까지 보내면 알림이 노이즈가 되어 아무도 안 본다.
 *
 * <p>두 가지를 지킨다.
 * <ul>
 *   <li><b>호출자를 절대 깨뜨리지 않는다</b> — 알림 전송 실패가 업무 로직 실패로 번지면
 *       알림을 붙인 것이 오히려 장애를 만든다. 모든 예외를 삼키고 로그만 남긴다.
 *   <li><b>호출자를 기다리게 하지 않는다</b> — 응급 발송 경로에서 불리므로 webhook 왕복이
 *       알림 지연으로 이어지면 안 된다. 전용 스레드에서 비동기로 보낸다.
 * </ul>
 */
@Slf4j
@Component
public class AlertNotifier {

    /** 밀린 알림을 붙잡아 두는 한도. 이보다 쌓이면 새 알림을 버린다 — 뒤늦은 장애 알림은 값이 없다. */
    private static final int ALERT_QUEUE_CAPACITY = 100;

    private final String webhookUrl;
    private final String environment;
    private final RestClient restClient;
    /**
     * 전용 단일 스레드 — {@code @EnableAsync}로 앱 전역 동작을 바꾸지 않고 이 컴포넌트만 격리한다.
     *
     * <p>큐를 유계로 두고 거부를 로그로 흡수하는 이유가 둘이다.
     * <ul>
     *   <li><b>종료 경로</b>: {@code shutdown()} 이후 {@code execute()}는
     *       {@code RejectedExecutionException}을 던진다. 그대로 두면 앱 종료 중 발생한
     *       응급 발송 실패가 호출자로 번져, "호출자를 깨뜨리지 않는다"는 이 클래스의 약속이 깨진다.
     *   <li><b>포화</b>: webhook이 매달리면 태스크가 무한히 쌓인다. 밀린 장애 알림은 어차피
     *       뒤늦게 도착해 쓸모가 적으므로, 쌓아 두느니 버리고 그 사실을 남긴다.
     * </ul>
     */
    private final ExecutorService sender = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(ALERT_QUEUE_CAPACITY),
            runnable -> {
                Thread thread = new Thread(runnable, "alert-notifier");
                thread.setDaemon(true);
                return thread;
            },
            (task, executor) -> log.warn("[ALERT] 알림 전송을 건너뜀 — 대기열 포화 또는 종료 중")
    );

    public AlertNotifier(
            @Value("${alert.webhook-url:}") String webhookUrl,
            @Value("${alert.environment:local}") String environment
    ) {
        this.webhookUrl = webhookUrl;
        this.environment = environment;
        // 주입 대신 직접 만든다 — 자동설정 유무에 관계없이 뜨고, 타임아웃을 여기서 못 박는다.
        // 발송 스레드가 하나뿐이라 webhook이 매달리면 이후 알림이 전부 밀린다.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().requestFactory(factory).build();
        if (webhookUrl.isBlank()) {
            log.info("[ALERT] webhook 미설정 — 치명 실패 알림 비활성 (ALERT_WEBHOOK_URL)");
        }
    }

    /**
     * 치명 실패 알림. webhook 미설정이면 조용히 지나간다.
     *
     * @param title   무엇이 실패했는지 한 줄
     * @param context 원인 추적에 필요한 키·값 (노인 ID 등). <b>개인정보·시크릿은 넣지 않는다</b>
     */
    public void critical(String title, Map<String, ?> context) {
        if (webhookUrl.isBlank()) {
            return;
        }
        String content = buildContent(title, context);
        sender.execute(() -> post(content));
    }

    private String buildContent(String title, Map<String, ?> context) {
        String detail = context.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(", "));
        return "🚨 [" + environment + "] " + title + (detail.isBlank() ? "" : "\n" + detail);
    }

    private void post(String content) {
        try {
            // Discord·Slack 모두 `content`/`text` 중 하나를 쓴다 — 둘 다 실어 어느 쪽이든 붙게 한다
            Map<String, String> body = new LinkedHashMap<>();
            body.put("content", content);
            body.put("text", content);
            restClient.post().uri(webhookUrl).body(body).retrieve().toBodilessEntity();
        } catch (Exception e) {
            // 알림 실패가 업무 실패로 번지면 안 된다 — 여기서 끝낸다
            log.warn("[ALERT] webhook 전송 실패: {}", e.getMessage());
        }
    }

    @PreDestroy
    void shutdown() {
        sender.shutdown();
        try {
            // 종료 직전 큐에 남은 알림을 잠깐 기다린다 — 마지막 장애 알림이 유실되면 곤란하다
            if (!sender.awaitTermination(3, TimeUnit.SECONDS)) {
                sender.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sender.shutdownNow();
        }
    }
}
