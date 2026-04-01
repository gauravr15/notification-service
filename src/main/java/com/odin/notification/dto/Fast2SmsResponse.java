package com.odin.notification.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Fast2SmsResponse {

    @JsonProperty("return")
    private boolean success;

    @JsonProperty("request_id")
    private String requestId;

    @JsonProperty("status_code")
    private int statusCode;

    private List<String> message;

    @JsonProperty("message_id")
    private String messageId;
}
