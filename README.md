# Financial Gateway with Intelligent API Fallback

A Spring Cloud Gateway application that provides transparent fallback between financial API providers. The gateway first attempts to call the Synth API, and if it fails (404, 5xx errors, etc.), it automatically falls back to the FMP (Financial Modeling Prep) API with intelligent endpoint transformation.

## Features

- **Transparent Fallback**: Automatically switches between API providers on failure
- **Intelligent Endpoint Transformation**: Maps Synth API endpoints to FMP API endpoints with correct URL patterns
- **Query Parameter Preservation**: Maintains original query parameters during fallback
- **Comprehensive Logging**: Detailed logs for debugging and monitoring
- **Original Route Preservation**: Callers continue using the same `/synth/**` endpoints

## Configuration

### Environment Variables

Set the following environment variable for FMP API key:

```bash
export FMP_API_KEY="your_fmp_api_key"
```

### Application Configuration

The application is configured via `application.yaml`:

```yaml
spring:
  cloud:
    gateway:
      routes:
      - id: synth
        uri: https://api.synthfinance.com/
        predicates:
          - Path=/synth/**
        filters:
          - RewritePath=/synth/(?<path>.*), /$\{path}
          - RemoveRequestHeader=Host
          - RemoveRequestHeader=X-Forwarded-Host
          - RemoveRequestHeader=X-Forwarded-Port
          - SynthFallback

# FMP API Configuration for fallback
fmp:
  api:
    base-url: https://financialmodelingprep.com/stable
    api-key: ${FMP_API_KEY:}
```

## Endpoint Transformation

The gateway intelligently transforms Synth API endpoints to FMP API endpoints:

### Company Profile
- **Synth API**: `/tickers/{symbol}`
- **FMP API**: `/profile?symbol={symbol}`

### Historical Price Data
- **Synth API**: `/tickers/{symbol}/open-close`
- **FMP API**: `/historical-price-full/{symbol}`

### Quote Data
- **Synth API**: `/quote/{symbol}`
- **FMP API**: `/quote/{symbol}` (same endpoint)

### Historical Price with Date Range
- **Synth API**: `/tickers/{symbol}/open-close?from={date}&to={date}`
- **FMP API**: `/historical-price-full/{symbol}?from={date}&to={date}`

### Search
- **Synth API**: `/search?q={query}`
- **FMP API**: `/search?query={query}`

## Usage

### API Endpoints

All financial API calls should be made to `/synth/*` endpoints. The gateway will automatically route them through the fallback mechanism.

#### Examples:

1. **Company Profile**:
   ```
   GET /synth/tickers/AAPL
   ```

2. **Historical Prices**:
   ```
   GET /synth/tickers/AAPL/open-close
   ```

3. **Historical Prices with Date Range**:
   ```
   GET /synth/tickers/AAPL/open-close?from=2023-01-01&to=2023-12-31
   ```

4. **Quote Data**:
   ```
   GET /synth/quote/AAPL
   ```

## How the Fallback Works

1. **Primary Call**: The gateway first attempts to call the Synth API
2. **Error Detection**: If the Synth API returns:
   - 404 (Not Found)
   - 5xx (Server Error)
   - Timeout
   - Connection errors
3. **Endpoint Transformation**: The gateway transforms the endpoint to match FMP API structure
4. **Fallback**: The gateway calls the FMP API with transformed endpoint and preserved query parameters
5. **Response**: Returns the successful response from either provider

### Transformation Logic

The gateway uses a pattern-based transformation system:

```java
// Company profile transformation
Pattern.compile("^/tickers/([^/]+)$") -> "/profile?symbol={symbol}"

// Historical price transformation  
Pattern.compile("^/tickers/([^/]+)/open-close$") -> "/historical-price-full/{symbol}"

// Quote data (same endpoint)
Pattern.compile("^/quote/([^/]+)$") -> "/quote/{symbol}"
```

## Building and Running

### Prerequisites

- Java 17+
- Gradle 7+

### Build

```bash
./gradlew build
```

### Run

```bash
./gradlew bootRun
```

### Test

```bash
./test-fallback.sh
```

## Monitoring and Logging

The application provides comprehensive logging at different levels:

- **DEBUG**: Detailed API call information
- **INFO**: Successful fallback operations and endpoint transformations
- **WARN**: API failures and fallback triggers
- **ERROR**: Complete failure scenarios

### Log Examples

```
INFO  - Processing Synth API request for endpoint: tickers/AAPL
WARN  - Synth API call failed for endpoint: tickers/AAPL, error: API not found - fallback triggered
INFO  - Triggering fallback to FMP API for endpoint: tickers/AAPL
INFO  - Calling FMP API as fallback: tickers/AAPL -> /profile with params: {symbol=AAPL}
INFO  - FMP API call successful for endpoint: /profile
```

## Error Handling

The gateway handles various error scenarios:

1. **API Not Found (404)**: Triggers fallback to FMP API
2. **Server Errors (5xx)**: Triggers fallback to FMP API
3. **Timeouts**: Configurable timeout with fallback
4. **All Providers Fail**: Returns 503 Service Unavailable with error details

## Development

### Project Structure

```
src/main/java/com/rainston/fingateway/
├── config/
│   ├── FmpConfig.java              # FMP API configuration
│   └── ApplicationConfig.java      # Application configuration
├── filter/
│   └── SynthFallbackFilter.java    # Gateway filter for fallback logic
├── service/
│   └── FmpFallbackService.java     # FMP API service with endpoint transformation
└── FinancialGatewayApplication.java # Main application class
```

### Adding New Endpoint Transformations

To add a new endpoint transformation:

1. Add a new pattern to the `transformers` map in `FmpFallbackService`
2. Implement the transformation logic in the `EndpointTransformer`
3. Test the transformation with the test script

Example:
```java
transformers.put(
    Pattern.compile("^/new-endpoint/([^/]+)$"),
    (matcher) -> new EndpointMapping("/fmp-endpoint/" + matcher.group(1), new HashMap<>())
);
```

## Troubleshooting

### Common Issues

1. **API Keys Not Configured**: Check environment variables
2. **Endpoint Mismatches**: Verify endpoint transformations in logs
3. **Query Parameter Issues**: Check if original parameters are preserved

### Debug Mode

Enable debug logging by setting:

```yaml
logging:
  level:
    com.rainston.fingateway: DEBUG
```

## License

This project is licensed under the MIT License. 