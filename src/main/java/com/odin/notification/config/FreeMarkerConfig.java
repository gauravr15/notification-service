package com.odin.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import freemarker.template.TemplateExceptionHandler;

@Configuration
public class FreeMarkerConfig {

	@Primary
    @Bean
    public freemarker.template.Configuration freemarkerConfiguration() {
        freemarker.template.Configuration cfg =
                new freemarker.template.Configuration(freemarker.template.Configuration.VERSION_2_3_31);

        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);

        return cfg;
    }
}
