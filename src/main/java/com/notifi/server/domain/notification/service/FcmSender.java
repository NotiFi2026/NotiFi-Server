package com.notifi.server.domain.notification.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FcmSender {

    private final ObjectProvider<FirebaseMessaging> firebaseMessagingProvider;

    /**
     * FCM 단건 발송. 발송 성공 시 true, 실패(SDK 미초기화 포함) 시 false 반환.
     */
    public boolean send(String token, String title, String body) {
        FirebaseMessaging messaging = firebaseMessagingProvider.getIfAvailable();
        if (messaging == null) {
            log.warn("[FCM] Firebase 미초기화 — 발송 건너뜀 (token prefix: {})",
                    token.length() > 10 ? token.substring(0, 10) : token);
            return false;
        }

        Message message = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
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
