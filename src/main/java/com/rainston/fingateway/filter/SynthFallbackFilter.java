package com.rainston.fingateway.filter;

import com.rainston.fingateway.service.FmpFallbackService;
import com.rainston.fingateway.service.ResponseTransformerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class SynthFallbackFilter extends AbstractGatewayFilterFactory<SynthFallbackFilter.Config> {
    
    private final FmpFallbackService fmpFallbackService;
    private final ResponseTransformerService responseTransformerService;
    private final WebClient webClient;
    
    public SynthFallbackFilter(FmpFallbackService fmpFallbackService, ResponseTransformerService responseTransformerService) {
        super(Config.class);
        this.fmpFallbackService = fmpFallbackService;
        this.responseTransformerService = responseTransformerService;
        this.webClient = WebClient.builder()
                .baseUrl("https://api.synthfinance.com")
                .build();
    }
    
    @Override
    public String name() {
        return "SynthFallback";
    }
    
    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getPath().value();
            
            // Extract the endpoint from the path (remove /synth prefix)
            String endpoint = path.startsWith("/synth/") ? path.substring(7) : path;
            
            log.info("Processing Synth API request for endpoint: {}", endpoint);
            
            // Read request body
            Mono<String> bodyMono = exchange.getRequest().getBody()
                    .reduce(exchange.getResponse().bufferFactory().wrap(new byte[0]), 
                            (prev, curr) -> {
                                DataBufferUtils.release(prev);
                                return curr;
                            })
                    .map(dataBuffer -> {
                        String body = dataBuffer.toString(StandardCharsets.UTF_8);
                        DataBufferUtils.release(dataBuffer);
                        return body;
                    })
                    .defaultIfEmpty("");
            
            return bodyMono.flatMap(body -> {
                // Build Synth API request
                WebClient.RequestBodySpec requestSpec = webClient.method(request.getMethod())
                        .uri(uriBuilder -> {
                            uriBuilder.path("/" + endpoint);
                            request.getQueryParams().forEach((key, values) -> {
                                if (!values.isEmpty()) {
                                    uriBuilder.queryParam(key, values.get(0));
                                }
                            });
                            return uriBuilder.build();
                        });
                
                // Copy headers (excluding Host)
                request.getHeaders().forEach((key, values) -> {
                    if (!"Host".equalsIgnoreCase(key)) {
                        requestSpec.header(key, values.toArray(new String[0]));
                    }
                });
                
                // Add request body if present
                if (!body.isEmpty()) {
                    requestSpec.bodyValue(body);
                }
                
                // Make request to Synth API
                return requestSpec.retrieve()
                        .bodyToMono(String.class)
                        .flatMap(synthResponse -> {
                            log.info("Synth API call successful for endpoint: {}", endpoint);
                            return writeResponse(exchange, HttpStatus.OK, synthResponse);
                        })
                        .onErrorResume(error -> {
                            log.warn("Synth API call failed for endpoint: {}, error: {}", endpoint, error.getMessage());
                            
                            // Check if this is a fallback-worthy error
                            if (shouldFallback(error)) {
                                log.info("Triggering fallback to FMP API for endpoint: {}", endpoint);
                                return callFmpAsFallback(exchange, endpoint, body);
                            }
                            
                            // If not fallback-worthy, return the original error
                            return Mono.error(error);
                        });
            });
        };
    }
    
    private boolean shouldFallback(Throwable error) {
        if (error instanceof WebClientResponseException) {
            WebClientResponseException wcre = (WebClientResponseException) error;
            int statusCode = wcre.getStatusCode().value();
            log.debug("WebClientResponseException with status: {}", statusCode);
            
            // Fallback on 4xx and 5xx errors
            return statusCode >= 400;
        }
        
        // Fallback on other errors like timeouts, connection issues
        log.debug("Non-HTTP error detected, triggering fallback: {}", error.getClass().getSimpleName());
        return true;
    }
    
    private Mono<Void> callFmpAsFallback(ServerWebExchange exchange, String endpoint, String originalBody) {
        ServerHttpRequest request = exchange.getRequest();
        
        // Extract query parameters from the original request
        Map<String, String> originalQueryParams = new HashMap<>();
        request.getQueryParams().forEach((key, values) -> {
            if (!values.isEmpty()) {
                originalQueryParams.put(key, values.get(0));
            }
        });
        
        return fmpFallbackService.callFmpApi(
                endpoint, 
                request.getMethod(), 
                request.getHeaders(), 
                originalBody.isEmpty() ? null : originalBody,
                originalQueryParams
        )
        .flatMap(fmpResponse -> {
            log.info("FMP API fallback successful for endpoint: {}", endpoint);
            
            // Transform FMP response to match Synth format
            String transformedResponse = responseTransformerService.transformFmpToSynthResponse(fmpResponse, endpoint);
            log.debug("Transformed FMP response to Synth format for endpoint: {}", endpoint);
            
            return writeResponse(exchange, HttpStatus.OK, transformedResponse);
        })
        .onErrorResume(fmpError -> {
            log.error("Both Synth and FMP API calls failed for endpoint: {}", endpoint, fmpError);
            String errorResponse = String.format(
                "{\"error\":\"Service unavailable\",\"message\":\"Both Synth and FMP APIs failed\",\"endpoint\":\"%s\"}",
                endpoint
            );
            return writeResponse(exchange, HttpStatus.SERVICE_UNAVAILABLE, errorResponse);
        });
    }
    
    private Mono<Void> writeResponse(ServerWebExchange exchange, HttpStatus status, String responseBody) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        
        DataBuffer responseBuffer = response.bufferFactory()
                .wrap(responseBody.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(responseBuffer));
    }
    
    public static class Config {
        // Configuration properties if needed
    }
} 