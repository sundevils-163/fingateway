# Financial Gateway with Intelligent API Fallback

A Spring Cloud Gateway application that provides transparent fallback between financial API providers. The gateway first attempts to call the Synth API, and if it fails (404, 5xx errors, etc.), it automatically falls back to the FMP (Financial Modeling Prep) API with intelligent endpoint and query parameter transformation.

## Features

- **Transparent Fallback**: Automatically switches between API providers on failure
- **Intelligent Endpoint Transformation**: Maps Synth API endpoints to FMP API endpoints with correct URL patterns
- **Query Parameter Transformation**: Converts Synth API query parameters to FMP API format within endpoint transformation
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
  application:
    name: fingateway
  codec:
    log-request-details: true
    log-response-details: true
  webflux:
    codec:
      max-in-memory-size: 10MB  # Increased buffer size for large responses
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
    v3-url: https://financialmodelingprep.com/api/v3
    api-key: ${FMP_API_KEY:}
```

## Endpoint and Query Parameter Transformation

The gateway intelligently transforms both endpoints and query parameters from Synth API format to FMP API format:

### Company Profile
- **Synth API**: `/tickers/{symbol}`
- **FMP API**: `/profile?symbol={symbol}`

### Historical Price Data
- **Synth API**: `/tickers/{symbol}/open-close`
- **FMP API**: `/historical-price-full/{symbol}`
- **Query Parameters**: `start_date` → `from`, `end_date` → `to`, `limit` preserved

### Quote Data
- **Synth API**: `/quote/{symbol}`
- **FMP API**: `/quote/{symbol}` (same endpoint)

### Search
- **Synth API**: `/search?q={query}`
- **FMP API**: `/search?query={query}` (parameter name transformation)

### Historical Price with Date Range
- **Synth API**: `/tickers/{symbol}/open-close?start_date=2023-01-01&end_date=2023-12-31`
- **FMP API**: `/historical-price-full/{symbol}?from=2023-01-01&to=2023-12-31`

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
   GET /synth/tickers/AAPL/open-close?start_date=2023-01-01&end_date=2023-12-31
   ```

4. **Quote Data**:
   ```
   GET /synth/quote/AAPL
   ```

5. **Search with Query Parameter**:
   ```
   GET /synth/search?q=AAPL
   ```

6. **Search with Multiple Parameters**:
   ```
   GET /synth/search?q=AAPL&limit=10
   ```

7. **Historical Prices with All Parameters**:
   ```
   GET /synth/tickers/AAPL/open-close?start_date=2023-01-01&end_date=2023-12-31&limit=30
   ```

## How the Fallback Works

1. **Primary Call**: The gateway first attempts to call the Synth API
2. **Error Detection**: If the Synth API returns:
   - 404 (Not Found)
   - 5xx (Server Error)
   - Timeout
   - Connection errors
3. **Endpoint and Query Parameter Transformation**: The gateway transforms both the endpoint and query parameters to match FMP API structure
4. **Fallback**: The gateway calls the FMP API with transformed endpoint and query parameters
5. **Response**: Returns the successful response from either provider

### Transformation Logic

The gateway uses a pattern-based transformation system that handles both endpoints and query parameters:

```java
// Company profile transformation
Pattern.compile("^/tickers/([^/]+)$") -> "/profile?symbol={symbol}"

// Historical price transformation with query params
Pattern.compile("^/tickers/([^/]+)/open-close$") -> "/historical-price-full/{symbol}?from={start_date}&to={end_date}&limit={limit}"

// Search with query parameter transformation
Pattern.compile("^/search$") -> "/search?query={q}&limit={limit}"

// Query parameter transformations
"q" -> "query" (for search endpoints)
"start_date" -> "from" (for historical data)
"end_date" -> "to" (for historical data)
"limit" -> preserved as-is
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

- **DEBUG**: Detailed API call information and query parameter transformations
- **INFO**: Successful fallback operations and endpoint transformations
- **WARN**: API failures and fallback triggers
- **ERROR**: Complete failure scenarios

### Log Examples

```
INFO  - Processing Synth API request for endpoint: tickers/AAPL
WARN  - Synth API call failed for endpoint: tickers/AAPL, error: API not found - fallback triggered
INFO  - Triggering fallback to FMP API for endpoint: tickers/AAPL
INFO  - Calling FMP API as fallback: tickers/AAPL -> /profile with params: {symbol=AAPL}
DEBUG - Query param transformation: q=AAPL -> query=AAPL
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
│   └── FmpFallbackService.java     # FMP API service with integrated endpoint and query parameter transformation
└── FinancialGatewayApplication.java # Main application class
```

### Adding New Endpoint Transformations

To add a new endpoint transformation with query parameter handling:

1. Add a new pattern to the `transformers` map in `FmpFallbackService.transformEndpointForFmp`
2. Implement the transformation logic in the `EndpointTransformer` lambda
3. Handle query parameter transformation within the same lambda
4. Test the transformation with the test script

Example:
```java
transformers.put(
    Pattern.compile("^/new-endpoint/([^/]+)$"),
    (matcher) -> {
        Map<String, String> params = new HashMap<>();
        // Handle query parameter transformation here
        if (originalQueryParams != null) {
            originalQueryParams.forEach((key, value) -> {
                if ("synthParam".equals(key)) {
                    params.put("fmpParam", value);
                } else {
                    params.put(key, value);
                }
            });
        }
        return new EndpointMapping("/fmp-endpoint/" + matcher.group(1), params);
    }
);
```

## Troubleshooting

### Common Issues

1. **Query Parameters Not Transformed**: Check the transformation logic within the specific endpoint pattern
2. **Endpoint Not Found**: Add new endpoint pattern to `transformEndpointForFmp` method
3. **Missing Query Parameters**: Verify the parameter handling logic in the endpoint transformer
4. **DataBufferLimitException**: Large responses from FMP API may exceed default buffer limits

### Debugging Query Parameter Transformation

Enable DEBUG logging to see query parameter transformations:

```yaml
logging:
  level:
    com.rainston.fingateway: DEBUG
```

This will show detailed logs like:
```
DEBUG - Query param transformation: q=AAPL -> query=AAPL
```

### Handling Large Responses

If you encounter `DataBufferLimitException` with large responses from FMP API:

1. **Check Current Buffer Size**: The application is configured with 10MB buffer size by default
2. **Increase Buffer Size**: If needed, increase the `max-in-memory-size` in `application.yaml`:
   ```yaml
   spring:
     webflux:
       codec:
         max-in-memory-size: 20MB  # Increase as needed
   ```
3. **Monitor Memory Usage**: Large buffer sizes increase memory consumption
4. **Consider Pagination**: For very large datasets, consider implementing pagination in your requests

## License

This project is licensed under the MIT License. 