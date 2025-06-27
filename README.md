# Web Crawler

A concurrent web crawler built with Java that crawls websites within domain boundaries, using Redis for state management and hexagonal architecture for clean code organization.

## Quick Start

### Prerequisites
- Java 21+
- Docker (for Redis)

### Running the Crawler

1. **Start Redis:**
```bash
docker run --name redis -p 6379:6379 redis
```

2. **Run the crawler:**
```bash
./gradlew run --args="https://monzo.com"
```

3. **For subsequent runs, flush Redis first:**
```bash
# Connect to Redis and flush
docker exec -it redis redis-cli FLUSHALL
```

**Note**: The crawler maintains state in Redis, so re-running without flushing will skip already crawled URLs.

### Example Output
```
16:30:45.123 [main] INFO  CrawlerApplication - Starting crawl at: https://monzo.com
16:30:45.124 [main] INFO  CrawlerApplication - Restricting to host: monzo.com

--------------------------------------------------
Crawled Page: https://monzo.com/
Found 8 total links (8 new):
  -> https://monzo.com/about
  -> https://monzo.com/business
  -> https://monzo.com/careers
  -> https://monzo.com/features
--------------------------------------------------
```

## Architecture

### Why Hexagonal Architecture?

**Benefits for this project:**
- **Testability**: Core logic isolated from Redis/HTTP dependencies
- **Flexibility**: Easy to swap Redis for database or HTTP client for different implementation
- **Clean boundaries**: Clear separation between business logic and infrastructure

**Structure:**
```
Application Layer    → WebCrawlerUseCase (orchestrates crawling)
Domain Layer        → CrawlStateService, PageProcessingService (business logic)
Infrastructure      → Redis adapters, HTTP client, Jsoup parser
```

### Why Redis?

**Chosen for simplicity and performance:**
- ✅ **Fast setup**: Single dependency for both queue and visited URL tracking
- ✅ **High performance**: In-memory operations, perfect for this use case
- ✅ **Atomic operations**: Thread-safe SADD/LPUSH operations
- ✅ **Persistence**: Survives application restarts

**Trade-offs vs Traditional Database:**
- ❌ **Memory usage**: All URLs stored in memory
- ❌ **Limited queries**: Can't do complex analysis of crawl data
- ❌ **Durability**: Less robust than ACID database
- ✅ **Simplicity**: No schema design, migrations, or complex setup needed

## Key Features

- **Concurrent crawling** with configurable limits (default: 80 concurrent requests)
- **Domain restriction** - only crawls within the starting domain
- **Duplicate prevention** - tracks visited URLs to avoid loops
- **Content filtering** - only processes HTML pages, skips images/PDFs
- **Robust error handling** - continues crawling despite individual page failures
- **Real-time progress** - shows crawled pages and discovered links

## How It Works

1. **Start with seed URL**: Adds initial URL to frontier queue
2. **Process queue**: Continuously dequeues URLs to crawl
3. **Fetch & parse**: Downloads HTML and extracts links using Jsoup
4. **Filter & normalize**: Removes external domains, normalizes URLs
5. **Track state**: Marks URLs as visited, adds new URLs to queue
6. **Repeat**: Continues until no more URLs to process

## Configuration

Configure via `src/main/resources/application.properties`:

```properties
# Redis connection
crawler.redis.url=redis://localhost:6379

# Performance tuning  
crawler.max.concurrent.requests=80
```

Override with environment variables:
```bash
export CRAWLER_MAX_CONCURRENT_REQUESTS=50
./gradlew run --args="https://monzo.com"
```

## Testing

The project includes comprehensive tests:

```bash
# Run all tests (requires Docker for Testcontainers)
./gradlew test

# Run specific test classes
./gradlew test --tests "*IntegrationTest"
```

**Test strategy:**
- **Unit tests**: Mock external dependencies, test business logic
- **Integration tests**: Real Redis + WireMock for HTTP
- **Component tests**: End-to-end crawling simulation

## Production Considerations

This implementation prioritizes **simplicity for the coding challenge**. For production use:

### Current Limitations
- **No robots.txt compliance** - doesn't respect crawling rules
- **Memory-bound storage** - Redis holds all URLs in memory
- **Single domain only** - can't crawl across multiple sites
- **No JavaScript rendering** - only parses static HTML
- **Basic rate limiting** - simple semaphore approach

### Production Improvements Needed
1. **Database migration**: PostgreSQL for durability and complex queries
2. **Robots.txt support**: Respect site crawling policies
3. **Enhanced error handling**: Exponential backoff, circuit breakers
4. **Monitoring**: Metrics, health checks, performance dashboards
5. **Advanced features**: JavaScript rendering, sitemap parsing, content deduplication

## Design Decisions

### URI Normalization
```java
// Handles URL variations:
https://monzo.com/page/ → https://monzo.com/page  (remove trailing slash)
https://monzo.com → https://monzo.com/             (add root path)
https://monzo.com/page#section → https://monzo.com/page (remove fragments)
```

### Concurrency Model
- **Virtual threads** (Java 21) for lightweight concurrency
- **Semaphore rate limiting** to control HTTP request load
- **Phaser coordination** for graceful shutdown

### Error Handling
- Network failures → logged and skipped, crawl continues
- HTTP errors (404, 500) → categorized and reported
- Parse errors → isolated to prevent crawler crash

## Why This Architecture?

**For a coding challenge**, this demonstrates:
- ✅ **Clean architecture principles** with clear separation of concerns
- ✅ **Proper abstraction** through ports and adapters pattern
- ✅ **Comprehensive testing** with different test strategies
- ✅ **Production awareness** while keeping scope manageable
- ✅ **Modern Java features** like virtual threads and records

The goal was to show **engineering maturity** while **keeping complexity reasonable** for a time-boxed challenge.

## Development

### Project Structure
```
src/main/java/com/monzo/crawler/
├── application/     # Use cases (WebCrawlerUseCase)
├── config/         # Factories and configuration  
├── domain/         # Business logic (services, models)
└── infrastructure/ # External adapters (Redis, HTTP, Jsoup)
```

### Key Classes
- `WebCrawlerUseCase`: Orchestrates the crawling process
- `CrawlStateService`: Manages visited URLs and frontier queue
- `PageProcessingService`: Fetches pages and extracts links
- `UriProcessingService`: Handles URL normalization and validation
