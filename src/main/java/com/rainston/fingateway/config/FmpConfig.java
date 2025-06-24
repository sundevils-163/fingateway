package com.rainston.fingateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "fmp.api")
public class FmpConfig {
    private String baseUrl = "https://financialmodelingprep.com/stable";
    private String v3Url = "https://financialmodelingprep.com/api/v3";
    private String apiKey;
} 