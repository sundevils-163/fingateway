#!/bin/bash

# Test script for Financial Gateway Fallback with Query Parameter Transformation
# Make sure the gateway is running on localhost:8080

BASE_URL="http://localhost:8080"

echo "=== Financial Gateway Fallback Test with Query Parameter Transformation ==="
echo

# Test 1: Company profile - Synth: /tickers/AAPL -> FMP: /profile?symbol=AAPL
echo "1. Testing company profile transformation..."
echo "   Synth API: /tickers/AAPL"
echo "   Expected FMP API: /profile?symbol=AAPL"
curl -s "$BASE_URL/synth/tickers/AAPL" | head -c 200
echo "..."
echo

# Test 2: Historical price data - Synth: /tickers/AAPL/open-close -> FMP: /historical-price-full/AAPL
echo "2. Testing historical price transformation..."
echo "   Synth API: /tickers/AAPL/open-close"
echo "   Expected FMP API: /historical-price-full/AAPL"
curl -s "$BASE_URL/synth/tickers/AAPL/open-close" | head -c 200
echo "..."
echo

# Test 3: Historical price with date range - Synth: /tickers/AAPL/open-close?start_date=2023-01-01&end_date=2023-12-31
echo "3. Testing historical price with date range..."
echo "   Synth API: /tickers/AAPL/open-close?start_date=2023-01-01&end_date=2023-12-31"
echo "   Expected FMP API: /historical-price-full/AAPL?from=2023-01-01&to=2023-12-31"
curl -s "$BASE_URL/synth/tickers/AAPL/open-close?start_date=2023-01-01&end_date=2023-12-31" | head -c 200
echo "..."
echo

# Test 4: Quote data - Synth: /quote/AAPL -> FMP: /quote/AAPL (same endpoint)
echo "4. Testing quote data (same endpoint)..."
echo "   Synth API: /quote/AAPL"
echo "   Expected FMP API: /quote/AAPL"
curl -s "$BASE_URL/synth/quote/AAPL" | head -c 200
echo "..."
echo

# Test 5: Search with query parameter transformation - Synth: /search?q=AAPL -> FMP: /search?query=AAPL
echo "5. Testing search with query parameter transformation..."
echo "   Synth API: /search?q=AAPL"
echo "   Expected FMP API: /search?query=AAPL"
curl -s "$BASE_URL/synth/search?q=AAPL" | head -c 200
echo "..."
echo

# Test 6: Search with multiple parameters - Synth: /search?q=AAPL&limit=10 -> FMP: /search?query=AAPL&limit=10
echo "6. Testing search with multiple parameters..."
echo "   Synth API: /search?q=AAPL&limit=10"
echo "   Expected FMP API: /search?query=AAPL&limit=10"
curl -s "$BASE_URL/synth/search?q=AAPL&limit=10" | head -c 200
echo "..."
echo

# Test 7: Historical price with limit parameter - Synth: /tickers/AAPL/open-close?limit=30 -> FMP: /historical-price-full/AAPL?limit=30
echo "7. Testing historical price with limit parameter..."
echo "   Synth API: /tickers/AAPL/open-close?limit=30"
echo "   Expected FMP API: /historical-price-full/AAPL?limit=30"
curl -s "$BASE_URL/synth/tickers/AAPL/open-close?limit=30" | head -c 200
echo "..."
echo

# Test 8: Historical price with all parameters - Synth: /tickers/AAPL/open-close?start_date=2023-01-01&end_date=2023-12-31&limit=30
echo "8. Testing historical price with all parameters..."
echo "   Synth API: /tickers/AAPL/open-close?start_date=2023-01-01&end_date=2023-12-31&limit=30"
echo "   Expected FMP API: /historical-price-full/AAPL?from=2023-01-01&to=2023-12-31&limit=30"
curl -s "$BASE_URL/synth/tickers/AAPL/open-close?start_date=2023-01-01&end_date=2023-12-31&limit=30" | head -c 200
echo "..."
echo

# Test 9: Non-existent ticker (should trigger fallback)
echo "9. Testing non-existent ticker (should trigger fallback)..."
echo "   Synth API: /tickers/INVALIDTICKER"
echo "   Expected FMP API: /profile?symbol=INVALIDTICKER"
curl -s "$BASE_URL/synth/tickers/INVALIDTICKER" | head -c 200
echo "..."
echo

echo "=== Test completed ==="
echo "Check the gateway logs to see the endpoint and query parameter transformations in action!"
echo "Expected log messages:"
echo "  - 'Calling FMP API as fallback: /tickers/AAPL -> /profile with params: {symbol=AAPL}'"
echo "  - 'Calling FMP API as fallback: /search -> /search with params: {query=AAPL}'"
echo "  - 'Query param transformation: q=AAPL -> query=AAPL'"
echo "  - 'Query param transformation: start_date=2023-01-01 -> from=2023-01-01'"
echo "  - 'Query param transformation: end_date=2023-12-31 -> to=2023-12-31'" 