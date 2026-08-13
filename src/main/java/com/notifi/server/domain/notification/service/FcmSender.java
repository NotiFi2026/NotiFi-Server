package com.notifi.server.domain.notification.service;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class FcmSender {

    /**
     * 발송 등급. Android 채널과 우선순위를 함께 결정한다.
     *
     * <p>모든 알림을 응급 채널로 보내면 안 된다. 보호자가 매일 오는 리포트 소리를 끄려고
     * 응급 채널을 무음으로 돌리는 순간 <b>진짜 낙상 알림까지 같이 죽는다.</b>
     * 등급을 나눠 사용자가 그 선택을 하지 않아도 되게 한다.
     */
    public enum Channel {
        /** 응급 — 잠금화면·도즈 상태에서도 즉시. 앱(expo-notifications)이 만드는 'emergency' 채널과 짝 */
        EMERGENCY("emergency", AndroidConfig.Priority.HIGH),
        /** 일반 — 앱 기본 채널·기본 중요도. 매일 도착하는 리포트처럼 급하지 않은 알림용 */
        NORMAL(null, AndroidConfig.Priority.NORMAL);

        private final String channelId;
        private final AndroidConfig.Priority priority;

        Channel(String channelId, AndroidConfig.Priority priority) {
            this.channelId = channelId;
            this.priority = priority;
        }
    }

    private final ObjectProvider<FirebaseMessaging> firebaseMessagingProvider;

    /**
     * FCM 단건 발송. 발송 성공 시 true, 실패(SDK 미초기화 포함) 시 false 반환.
     */
    public boolean send(String token, String title, String body) {
        return send(token, title, body, Map.of());
    }

    /**
     * data 페이로드 포함 발송 — 앱이 알림 탭 시 특정 화면(음성확인 UI 등)으로 라우팅하는 키를 담는다.
     * 등급 미지정 시 응급으로 보낸다 — 기존 호출부(에스컬레이션)의 동작을 그대로 유지하기 위함이다.
     */
    public boolean send(String token, String title, String body, Map<String, String> data) {
        return send(token, title, body, data, Channel.EMERGENCY);
    }

    public boolean send(String token, String title, String body, Map<String, String> data,
                        Channel channel) {
        FirebaseMessaging messaging = firebaseMessagingProvider.getIfAvailable();
        if (messaging == null) {
            log.warn("[FCM] Firebase 미초기화 — 발송 건너뜀 (token prefix: {})",
                    token.length() > 10 ? token.substring(0, 10) : token);
            return false;
        }

        AndroidConfig.Builder android = AndroidConfig.builder().setPriority(channel.priority);
        if (channel.channelId != null) {
            // 앱이 만든 채널과 이름이 일치해야 한다. 미지정이면 Android가 앱 기본 채널로
            // 떨어뜨리는데, 일반 등급에는 그게 의도한 결과다.
            android.setNotification(AndroidNotification.builder()
                    .setChannelId(channel.channelId)
                    .build());
        }

        Message message = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .putAllData(data)
                .setAndroidConfig(android.build())
                .build();

        try {
            messaging.send(message);
            return true;
        } catch (FirebaseMessagingException e) {
            log.warn("[FCM] 발송 실패 (token prefix: {}, errorCode: {})",
                    token.length() > 10 ? token.substring(0, 10) : token,
                    e.getMessagingErrorCode(), e);
            return false;
        }
    }
}
