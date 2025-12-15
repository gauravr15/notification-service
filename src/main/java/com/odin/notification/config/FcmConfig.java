package com.odin.notification.config;

import java.io.FileInputStream;
import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import lombok.extern.slf4j.Slf4j;

/**
 * Firebase Cloud Messaging (FCM) Configuration
 * Initializes the Firebase Admin SDK with service account credentials
 */
@Slf4j
@Configuration
public class FcmConfig {

    @Value("${fcm.service-account-path}")
    private String serviceAccountPath;

    @Value("${fcm.project-id}")
    private String projectId;

    /**
     * Initialize Firebase Admin SDK
     * @return FirebaseApp instance
     * @throws IOException if service account file cannot be read
     */
    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        log.info("Initializing Firebase Admin SDK with service account from: {}", serviceAccountPath);

        // Check if Firebase app is already initialized
        if (FirebaseApp.getApps().isEmpty()) {
            try {
                GoogleCredentials credentials = GoogleCredentials
                        .fromStream(new FileInputStream(serviceAccountPath));

                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(credentials)
                        .setProjectId(projectId)
                        .build();

                FirebaseApp.initializeApp(options);
                log.info("Firebase Admin SDK initialized successfully with project: {}", projectId);
            } catch (IOException e) {
                log.error("Failed to initialize Firebase Admin SDK. Service account file not found at: {}", 
                        serviceAccountPath);
                log.error("Error details: {}", e.getMessage());
                throw new IOException("Failed to initialize Firebase Admin SDK. " +
                        "Please ensure the service account JSON file exists at: " + serviceAccountPath, e);
            }
        } else {
            log.debug("Firebase Admin SDK already initialized");
        }

        return FirebaseApp.getInstance();
    }

    /**
     * Provide FirebaseMessaging instance for sending messages
     * @param firebaseApp The initialized FirebaseApp bean (ensures initialization order)
     * @return FirebaseMessaging instance
     */
    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        log.debug("Creating FirebaseMessaging bean");
        return FirebaseMessaging.getInstance();
    }
}


