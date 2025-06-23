package com.rainston.fingateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ResponseTransformerService {
    
    private final ObjectMapper objectMapper;
    
    public ResponseTransformerService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    public String transformFmpToSynthResponse(String fmpResponse, String endpoint) {
        try {
            JsonNode fmpNode = objectMapper.readTree(fmpResponse);
            
            // Transform based on endpoint pattern
            if (Pattern.matches("^/tickers/[^/]+/open-close$", endpoint)) {
                return transformHistoricalData(fmpNode, endpoint);
            } else if (Pattern.matches("^/tickers/[^/]+$", endpoint)) {
                return transformCompanyProfile(fmpNode, endpoint);
            } else if (Pattern.matches("^/quote/[^/]+$", endpoint)) {
                return transformQuoteData(fmpNode, endpoint);
            } else if (Pattern.matches("^/search$", endpoint)) {
                return transformSearchResults(fmpNode, endpoint);
            } else {
                // Default: return as-is if no specific transformation
                log.warn("No specific transformation found for endpoint: {}, returning FMP response as-is", endpoint);
                return fmpResponse;
            }
        } catch (Exception e) {
            log.error("Error transforming FMP response for endpoint: {}", endpoint, e);
            // Return original response if transformation fails
            return fmpResponse;
        }
    }
    
    private String transformHistoricalData(JsonNode fmpNode, String endpoint) {
        try {
            // Extract symbol from endpoint
            String symbol = fmpNode.has("symbol") ? fmpNode.get("symbol").asText() : extractSymbolFromEndpoint(endpoint);
            
            ObjectNode synthResponse = objectMapper.createObjectNode();
            synthResponse.put("ticker", symbol);
            synthResponse.put("currency", "USD");
            synthResponse.put("type", "historical");
            
            ArrayNode dataArray = synthResponse.putArray("prices");
            
            if (fmpNode.has("historical")) {
                JsonNode historicalArray = fmpNode.get("historical");
                if (historicalArray.isArray()) {
                    for (JsonNode day : historicalArray) {
                        ObjectNode dayData = dataArray.addObject();
                        dayData.put("date", day.get("date").asText());
                        dayData.put("open", day.get("open").asDouble());
                        dayData.put("high", day.get("high").asDouble());
                        dayData.put("low", day.get("low").asDouble());
                        dayData.put("close", day.get("close").asDouble());
                        dayData.put("volume", day.get("volume").asLong());
                    }
                }
            }
            
            return objectMapper.writeValueAsString(synthResponse);
        } catch (Exception e) {
            log.error("Error transforming historical data", e);
            return fmpNode.toString();
        }
    }
    
    private String transformCompanyProfile(JsonNode fmpNode, String endpoint) {
        try {
            log.info("transformCompanyProfile: {}", fmpNode.toString());

            // Extract symbol from endpoint
            String symbol = extractSymbolFromEndpoint(endpoint);
            
            ObjectNode synthResponse = objectMapper.createObjectNode();
            synthResponse.put("symbol", symbol);
            synthResponse.put("type", "profile");
            
            // FMP returns an array, take the first element
            JsonNode profileData = fmpNode.isArray() && fmpNode.size() > 0 ? fmpNode.get(0) : fmpNode;
            
            ObjectNode data = synthResponse.putObject("data");
            ObjectNode marketData = data.putObject("market_data");
            ObjectNode address = data.putObject("address");
            
            // Map FMP fields to Synth format
            if (profileData.has("symbol")) {
                data.put("ticker", profileData.get("symbol").asText());
            }
            if (profileData.has("companyName")) {
                data.put("name", profileData.get("companyName").asText());
            }
            if (profileData.has("currency")) {
                data.put("currency", profileData.get("currency").asText());
            }
            if (profileData.has("cik")) {
                data.put("cik", profileData.get("cik").asText());
            }
            if (profileData.has("changes")) {
                data.put("change", profileData.get("changes").asDouble());
            }
            if (profileData.has("marketCap")) {
                data.put("marketCap", profileData.get("marketCap").asLong());
            }
            if (profileData.has("sector")) {
                data.put("sector", profileData.get("sector").asText());
            }
            if (profileData.has("industry")) {
                data.put("industry", profileData.get("industry").asText());
            }
            if (profileData.has("description")) {
                data.put("description", profileData.get("description").asText());
            }
            if (profileData.has("website")) {
                data.put("website", profileData.get("website").asText());
            }
            if (profileData.has("ceo")) {
                data.put("ceo", profileData.get("ceo").asText());
            }
            if (profileData.has("fullTimeEmployees")) {
                data.put("total_employees", profileData.get("fullTimeEmployees").asInt());
            }
            if (profileData.has("phone")) {
                data.put("phone", profileData.get("phone").asText());
            }

            // market data
            if (profileData.has("price")) {
                marketData.put("close_today", profileData.get("price").asText());
            }
            if (profileData.has("volume")) {
                marketData.put("volume_today", profileData.get("volume").asText());
            }

            // address
            if (profileData.has("address")) {
                address.put("address_line1", profileData.get("address").asText());
            }
            if (profileData.has("city")) {
                address.put("city", profileData.get("city").asText());
            }
            if (profileData.has("country")) {
                address.put("country", profileData.get("country").asText());
            }
            if (profileData.has("zip")) {
                address.put("postal_code", profileData.get("zip").asText());
            }
            if (profileData.has("state")) {
                address.put("state", profileData.get("state").asText());
            }
            
            log.info("synthResponse: {}", synthResponse.toString());
            return objectMapper.writeValueAsString(synthResponse);
        } catch (Exception e) {
            log.error("Error transforming company profile", e);
            return fmpNode.toString();
        }
    }
    
    private String transformQuoteData(JsonNode fmpNode, String endpoint) {
        try {
            // Extract symbol from endpoint
            String symbol = extractSymbolFromEndpoint(endpoint);
            
            ObjectNode synthResponse = objectMapper.createObjectNode();
            synthResponse.put("symbol", symbol);
            synthResponse.put("type", "quote");
            
            // FMP returns an array, take the first element
            JsonNode quoteData = fmpNode.isArray() && fmpNode.size() > 0 ? fmpNode.get(0) : fmpNode;
            
            ObjectNode data = synthResponse.putObject("data");
            
            // Map FMP fields to Synth format
            if (quoteData.has("price")) {
                data.put("price", quoteData.get("price").asDouble());
            }
            if (quoteData.has("changes")) {
                data.put("change", quoteData.get("changes").asDouble());
            }
            if (quoteData.has("changePercent")) {
                data.put("changePercent", quoteData.get("changePercent").asDouble());
            }
            if (quoteData.has("dayLow")) {
                data.put("dayLow", quoteData.get("dayLow").asDouble());
            }
            if (quoteData.has("dayHigh")) {
                data.put("dayHigh", quoteData.get("dayHigh").asDouble());
            }
            if (quoteData.has("yearLow")) {
                data.put("yearLow", quoteData.get("yearLow").asDouble());
            }
            if (quoteData.has("yearHigh")) {
                data.put("yearHigh", quoteData.get("yearHigh").asDouble());
            }
            if (quoteData.has("marketCap")) {
                data.put("marketCap", quoteData.get("marketCap").asLong());
            }
            if (quoteData.has("volume")) {
                data.put("volume", quoteData.get("volume").asLong());
            }
            if (quoteData.has("avgVolume")) {
                data.put("avgVolume", quoteData.get("avgVolume").asLong());
            }
            if (quoteData.has("open")) {
                data.put("open", quoteData.get("open").asDouble());
            }
            if (quoteData.has("previousClose")) {
                data.put("previousClose", quoteData.get("previousClose").asDouble());
            }
            if (quoteData.has("eps")) {
                data.put("eps", quoteData.get("eps").asDouble());
            }
            if (quoteData.has("pe")) {
                data.put("pe", quoteData.get("pe").asDouble());
            }
            if (quoteData.has("earningsAnnouncement")) {
                data.put("earningsAnnouncement", quoteData.get("earningsAnnouncement").asText());
            }
            if (quoteData.has("sharesOutstanding")) {
                data.put("sharesOutstanding", quoteData.get("sharesOutstanding").asLong());
            }
            if (quoteData.has("timestamp")) {
                data.put("timestamp", quoteData.get("timestamp").asLong());
            }
            
            return objectMapper.writeValueAsString(synthResponse);
        } catch (Exception e) {
            log.error("Error transforming quote data", e);
            return fmpNode.toString();
        }
    }
    
    private String transformSearchResults(JsonNode fmpNode, String endpoint) {
        try {
            ObjectNode synthResponse = objectMapper.createObjectNode();
            synthResponse.put("type", "search");
            
            ArrayNode resultsArray = synthResponse.putArray("results");
            
            if (fmpNode.isArray()) {
                for (JsonNode result : fmpNode) {
                    ObjectNode resultData = resultsArray.addObject();
                    
                    if (result.has("symbol")) {
                        resultData.put("symbol", result.get("symbol").asText());
                    }
                    if (result.has("name")) {
                        resultData.put("name", result.get("name").asText());
                    }
                    if (result.has("currency")) {
                        resultData.put("currency", result.get("currency").asText());
                    }
                    if (result.has("stockExchange")) {
                        resultData.put("exchange", result.get("stockExchange").asText());
                    }
                    if (result.has("exchangeShortName")) {
                        resultData.put("exchangeShort", result.get("exchangeShortName").asText());
                    }
                }
            }
            
            return objectMapper.writeValueAsString(synthResponse);
        } catch (Exception e) {
            log.error("Error transforming search results", e);
            return fmpNode.toString();
        }
    }
    
    private String extractSymbolFromEndpoint(String endpoint) {
        // Extract symbol from patterns like /tickers/AAPL or /quote/AAPL
        String[] parts = endpoint.split("/");
        if (parts.length >= 3) {
            return parts[2];
        }
        return "UNKNOWN";
    }
} 