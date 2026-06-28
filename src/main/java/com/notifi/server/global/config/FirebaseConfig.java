package com.notifi.server.global.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${firebase.credentials-path:}")
    private String credentialsPath;

    @Bean
    public FirebaseMessaging firebaseMessaging() throws IOException {
        if (credentialsPath == null || credentialsPath.isBlank()) {
            log.warn("[FCM] FIREBASE_CREDENTIALS_PATH 미설정 — FCM 발송 비활성화 (알림은 FAILED로 기록됨)");
            return null;
        }

        try (InputStream serviceAccount = new FileInputStream(credentialsPath)) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
            }

            log.info("[FCM] Firebase 초기화 완료 (credentials: {})", credentialsPath);
            return FirebaseMessaging.getInstance();
        } catch (IOException e) {
            log.error("[FCM] Firebase 초기화 실패 — 파일을 읽을 수 없음: {}", credentialsPath, e);
            return null;
        }
    }
}
