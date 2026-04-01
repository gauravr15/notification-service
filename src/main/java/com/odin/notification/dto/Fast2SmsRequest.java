package com.odin.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Fast2SmsRequest {

    private String route;

    private String message;

    @JsonProperty("language")
    private String language;

    @JsonProperty("flash")
    private int flash;

    private String numbers;

    @JsonProperty("schedule_time")
    private String scheduleTime;
}
