package com.odin.notification.dto;

import java.util.Map;
import java.util.HashMap;

import com.odin.notification.enums.NotificationChannel;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class NotificationDTO {
    private Long customerId;
    private Long notificationId;
    private NotificationChannel channel;
    @JsonProperty("map")
    private Map<String, Object> map = new HashMap<>();
    private String mobile;
    private String email;

    /**
     * Capture any unknown fields from the Kafka message and put them in the map.
     */
   @JsonAnySetter
   public void addUnknownField(String key, Object value) {
       if (map == null) map = new HashMap<>();
       map.putIfAbsent(key, value);
   }

    /**
     * Get sender mobile from map
     * @return sender mobile phone number
     */
    public String getSenderMobile() {
        if (map == null) return null;
        Object val = map.get("senderMobile");
        return val != null ? String.valueOf(val) : null;
    }

    /**
     * Get sender customer ID from map
     * @return sender customer ID
     */
    public String getSenderCustomerId() {
        if (map == null) return null;
        Object val = map.get("senderCustomerId");
        return val != null ? String.valueOf(val) : null;
    }

    /**
     * Get sender name from map
     * @return sender name or display name
     */
    public String getSenderName() {
        if (map == null) return null;
        Object val = map.get("senderName");
        if (val == null) val = map.get("displayName");
        return val != null ? String.valueOf(val) : "New Message";
    }

    /**
     * Get conversation ID from map
     * @return conversation ID
     */
    public String getConversationId() {
        if (map == null) return null;
        Object val = map.get("conversationId");
        return val != null ? String.valueOf(val) : null;
    }

    /**
     * Get message from map
     * @return message body
     */
    public String getMessage() {
        if (map == null) return null;
        Object val = map.get("message");
        if (val == null) val = map.get("sampleMessage");
        return val != null ? String.valueOf(val) : null;
    }

    /**
     * Check if the notification is encrypted
     * @return true if encryption fields are present
     */
    public boolean isEncrypted() {
        if (map == null) return false;
        return map.containsKey("ciphertext") && map.containsKey("iv") && map.containsKey("tag");
    }

    /**
     * Get ciphertext from map
     * @return ciphertext
     */
    public String getCiphertext() {
        if (map == null) return null;
        Object val = map.get("ciphertext");
        return val != null ? String.valueOf(val) : null;
    }

    /**
     * Get file IDs from map (returns as comma-separated string from JSON array representation)
     * @return file IDs
     */
    @SuppressWarnings("unchecked")
    public String getFileIds() {
        if (map == null) return null;
        Object files = map.get("files");
        if (files instanceof java.util.List) {
            java.util.List<Object> list = (java.util.List<Object>) files;
            return list.stream()
                    .map(String::valueOf)
                    .collect(java.util.stream.Collectors.joining(","));
        } else if (files != null) {
            return String.valueOf(files);
        }
        return null;
    }
}
