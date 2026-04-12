package com.odin.notification.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import com.odin.notification.dto.NotificationDTO;
import com.odin.notification.dto.PrivacyVisibilityChangeEvent;


@EnableKafka
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Value("${spring.kafka.consumer.key-deserializer}")
    private String keyDeserializer;

    @Value("${spring.kafka.consumer.value-deserializer}")
    private String valueDeserializer;

    @Value("${spring.kafka.consumer.trusted-packages}")
    private String trustedPackages;

    @Bean
    public ConsumerFactory<String, NotificationDTO> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, keyDeserializer);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, valueDeserializer);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, trustedPackages);
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(),
                new JsonDeserializer<>(NotificationDTO.class, false));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, NotificationDTO> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, NotificationDTO> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        return factory;
    }

    /**
     * Consumer factory specifically for PrivacyVisibilityChangeEvent deserialization.
     * This separate factory ensures privacy change events are properly deserialized
     * instead of being converted to NotificationDTO.
     * 
     * Configuration:
     * - useTypeHeaders=false: Disable Jackson type info (profile-service uses com.odin.profileservice.dto.* 
     *   but notification-service has com.odin.notification.dto.* - both are identical structures)
     * - Explicitly set VALUE_DEFAULT_TYPE to notification-service's PrivacyVisibilityChangeEvent
     */
    @Bean
    public ConsumerFactory<String, PrivacyVisibilityChangeEvent> privacyVisibilityChangeConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        
        // CRITICAL: Disable type headers to avoid cross-service class resolution
        // Profile-service publishes with com.odin.profileservice.dto.PrivacyVisibilityChangeEvent
        // Notification-service needs to deserialize as com.odin.notification.dto.PrivacyVisibilityChangeEvent
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, PrivacyVisibilityChangeEvent.class.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES, trustedPackages);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Listener container factory for privacy visibility change events.
     * Must be explicitly used by @KafkaListener for privacy-visibility-updates topic.
     */
    @Bean("privacyVisibilityChangeListenerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, PrivacyVisibilityChangeEvent>
    privacyVisibilityChangeListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, PrivacyVisibilityChangeEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(privacyVisibilityChangeConsumerFactory());
        return factory;
    }
}
