package com.rainston.fingateway.config;

import com.rainston.fingateway.filter.SynthFallbackFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RouteConfig {
    
    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder, SynthFallbackFilter synthFallbackFilter) {
        return builder.routes()
                .route("synth", r -> r
                        .path("/synth/**")
                        .filters(f -> f
                                .rewritePath("/synth/(?<path>.*)", "/${path}")
                                .removeRequestHeader("Host")
                                .removeRequestHeader("X-Forwarded-Host")
                                .removeRequestHeader("X-Forwarded-Port")
                                .filter(synthFallbackFilter.apply(new SynthFallbackFilter.Config()))
                        )
                        .uri("no://op")) // This URI is not used since the filter handles the request
                .build();
    }
} 