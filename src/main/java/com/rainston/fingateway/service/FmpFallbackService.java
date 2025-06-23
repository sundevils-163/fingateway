package com.rainston.fingateway.service;

import com.rainston.fingateway.config.FmpConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class FmpFallbackService {
    
    private final FmpConfig fmpConfig;
    private final WebClient.Builder webClientBuilder;
    
    public Mono<String> callFmpApi(String endpoint, HttpMethod method, HttpHeaders headers, String body, Map<String, String> originalQueryParams) {
        WebClient webClient = webClientBuilder
                .baseUrl(fmpConfig.getBaseUrl())
                .build();
        
        // Transform endpoint for FMP API
        EndpointMapping fmpMapping = transformEndpointForFmp(endpoint);
        
        log.info("Calling FMP API as fallback: {} -> {} with params: {}", endpoint, fmpMapping.getPath(), fmpMapping.getQueryParams());
        
        WebClient.RequestBodySpec requestSpec = webClient.method(method)
                .uri(uriBuilder -> {
                    uriBuilder.path(fmpMapping.getPath());
                    
                    // Add transformed query parameters
                    fmpMapping.getQueryParams().forEach(uriBuilder::queryParam);
                    
                    // Add original query parameters (these will override transformed ones if there are conflicts)
                    if (originalQueryParams != null) {
                        originalQueryParams.forEach(uriBuilder::queryParam);
                    }
                    
                    // Add FMP API key if configured
                    if (fmpConfig.getApiKey() != null && !fmpConfig.getApiKey().isEmpty()) {
                        uriBuilder.queryParam("apikey", fmpConfig.getApiKey());
                    }
                    return uriBuilder.build();
                });
        
        // Copy relevant headers from original request
        if (headers != null) {
            headers.forEach((key, values) -> {
                if (!key.equalsIgnoreCase("host") && !key.equalsIgnoreCase("content-length")) {
                    requestSpec.header(key, values.toArray(new String[0]));
                }
            });
        }
        
        // Set content type if not present
        if (headers == null || headers.getContentType() == null) {
            requestSpec.contentType(MediaType.APPLICATION_JSON);
        }
        
        // Handle request body for POST/PUT requests
        if ((method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH) 
                && body != null && !body.isEmpty()) {
            return requestSpec.bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .doOnSuccess(result -> log.info("FMP API call successful for endpoint: {}", fmpMapping.getPath()))
                    .doOnError(error -> log.error("FMP API call failed for endpoint: {}", fmpMapping.getPath(), error));
        } else {
            return requestSpec.retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .doOnSuccess(result -> log.info("FMP API call successful for endpoint: {}", fmpMapping.getPath()))
                    .doOnError(error -> log.error("FMP API call failed for endpoint: {}", fmpMapping.getPath(), error));
        }
    }
    
    // Overloaded method for backward compatibility
    public Mono<String> callFmpApi(String endpoint, HttpMethod method, HttpHeaders headers, String body) {
        return callFmpApi(endpoint, method, headers, body, new HashMap<>());
    }
    
    private EndpointMapping transformEndpointForFmp(String synthEndpoint) {
        // Define patterns for different Synth API endpoints
        Map<Pattern, EndpointTransformer> transformers = new HashMap<>();
        
        // Historical price data: /tickers/{symbol}/open-close -> /historical-price-full/{symbol}
        transformers.put(
            Pattern.compile("^/tickers/([^/]+)/open-close$"),
            (matcher) -> new EndpointMapping("/historical-price-full/" + matcher.group(1), new HashMap<>())
        );
        
        // Company profile: /tickers/{symbol} -> /profile?symbol={symbol}
        transformers.put(
            Pattern.compile("^/tickers/([^/]+)$"),
            (matcher) -> {
                Map<String, String> params = new HashMap<>();
                params.put("symbol", matcher.group(1));
                return new EndpointMapping("/profile", params);
            }
        );
        
        // Quote data: /quote/{symbol} -> /quote/{symbol}
        transformers.put(
            Pattern.compile("^/quote/([^/]+)$"),
            (matcher) -> new EndpointMapping("/quote/" + matcher.group(1), new HashMap<>())
        );
        
        // Historical price with date range: /tickers/{symbol}/open-close?from={date}&to={date} -> /historical-price-full/{symbol}?from={date}&to={date}
        transformers.put(
            Pattern.compile("^/tickers/([^/]+)/open-close$"),
            (matcher) -> new EndpointMapping("/historical-price-full/" + matcher.group(1), new HashMap<>())
        );
        
        // Search: /search?q={query} -> /search?query={query}
        transformers.put(
            Pattern.compile("^/search$"),
            (matcher) -> new EndpointMapping("/search", new HashMap<>())
        );
        
        // Try to match the endpoint with our transformers
        for (Map.Entry<Pattern, EndpointTransformer> entry : transformers.entrySet()) {
            Matcher matcher = entry.getKey().matcher(synthEndpoint);
            if (matcher.matches()) {
                return entry.getValue().transform(matcher);
            }
        }
        
        // If no specific pattern matches, try to handle common cases
        if (synthEndpoint.startsWith("/tickers/")) {
            // Generic ticker endpoint - try to extract symbol and map to profile
            String[] parts = synthEndpoint.split("/");
            if (parts.length >= 3) {
                String symbol = parts[2];
                Map<String, String> params = new HashMap<>();
                params.put("symbol", symbol);
                return new EndpointMapping("/profile", params);
            }
        }
        
        // Default fallback - return the original endpoint
        log.warn("No specific transformation found for endpoint: {}, using as-is", synthEndpoint);
        return new EndpointMapping(synthEndpoint, new HashMap<>());
    }
    
    @FunctionalInterface
    private interface EndpointTransformer {
        EndpointMapping transform(Matcher matcher);
    }
    
    public static class EndpointMapping {
        private final String path;
        private final Map<String, String> queryParams;
        
        public EndpointMapping(String path, Map<String, String> queryParams) {
            this.path = path;
            this.queryParams = queryParams;
        }
        
        public String getPath() {
            return path;
        }
        
        public Map<String, String> getQueryParams() {
            return queryParams;
        }
    }
} 