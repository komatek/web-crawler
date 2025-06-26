# Web Crawler

A high-performance, concurrent web crawler built with Java 21 using hexagonal architecture principles. The crawler is designed to efficiently crawl websites while respecting domain boundaries and avoiding duplicate processing.

## Features

- **Concurrent Processing**: Utilizes Java 21 virtual threads for high-performance concurrent crawling
- **Domain Restriction**: Crawls only within the specified domain to respect boundaries
- **Duplicate Detection**: Redis-backed visited URL tracking prevents processing the same page twice
- **Rate Limiting**: Configurable concurrent request limiting to be respectful to target servers
- **Robust Error Handling**: Comprehensive error handling for network issues, timeouts, and malformed content
- **Link Filtering**: Automatically filters out static files and non-HTTP(S) links
- **URI Normalization**: Normalizes URIs to prevent duplicate processing of equivalent URLs
- **Comprehensive Logging**: Detailed logging with configurable levels for monitoring and debugging

## Architecture

The project follows hexagonal architecture (ports and adapters) principles:

```
├── domain/
│   ├── model/          # Core domain models (PageData)
│   └── port/out/       # Output ports (interfaces)
├── application/        # Use cases and business logic
└── infrastructure/     # Adapters and external integrations
```

### Core Components

- **WebCrawler**: Main orchestrator managing the crawling process
- **PageFetcher**: HTTP client for fetching web pages
- **LinkExtractor**: Extracts and validates links from HTML content
- **FrontierQueue**: Redis-backed queue for managing URLs to crawl
- **VisitedRepository**: Redis-backed storage for tracking visited URLs
- **CrawlObserver**: Observer for crawl events and results

## Prerequisites

- **Java 21** or higher
- **Docker** (for Redis and integration tests)
- **Gradle 8.5+**

## Quick Start

### 1. Clone the Repository

```bash
git clone <repository-url>
cd web-crawler
```

### 2. Start Redis

```bash
docker run -d --name redis -p 6379:6379 redis:7-alpine
```

### 3. Build the Project

```bash
./gradlew build
```

### 4. Run the Crawler

```bash
# Using Gradle
./gradlew run --args="https://example.com"

# Using the JAR file
java -jar build/libs/web-crawler-1.0.0.jar https://example.com
```

## Configuration

### Redis Configuration

The crawler connects to Redis at `redis://localhost:6379` by default. You can override this by:

1. **Environment Variable**:
   ```bash
   export CRAWLER_REDIS_URL=redis://your-redis-host:6379
   ```

2. **System Property**:
   ```bash
   java -Dcrawler.redis.url=redis://your-redis-host:6379 -jar crawler.jar https://example.com
   ```

3. **Application Properties**:
   ```properties
   crawler.redis.url=redis://your-redis-host:6379
   ```

### Crawler Settings

Key configuration options in the `CrawlerApplication.java`:

- **Max Concurrent Requests**: Currently set to 90 concurrent requests
- **HTTP Timeout**: 5 seconds for HTTP connections
- **Rate Limiting**: Managed via Semaphore to control concurrent requests

## Usage Examples

### Basic Crawling

```bash
# Crawl a website starting from the homepage
./gradlew run --args="https://monzo.com"
```

### With Custom Redis

```bash
# Using custom Redis instance
CRAWLER_REDIS_URL=redis://custom-redis:6379 ./gradlew run --args="https://example.com"
```

### Building and Running JAR

```bash
# Build the fat JAR
./gradlew jar

# Run the JAR
java -jar build/libs/web-crawler-1.0.0.jar https://example.com
```

## Development

### Project Structure

```
src/
├── main/java/com/monzo/crawler/
│   ├── application/           # Use cases
│   │   ├── WebCrawler.java
│       └── WebCrawlerUseCase.java
│   ├── domain/
│   │   ├── model/            # Domain models
│   │   │   └── PageData.java
│   │   └── port/out/         # Output ports
│   │       ├── PageFetcher.java
│   │       ├── LinkExtractor.java
│   │       ├── FrontierQueue.java
│   │       ├── VisitedRepository.java
│   │       └── CrawlObserver.java
│   ├── infrastructure/       # Adapters
│   │   ├── config/         # Configuration classes
│   │   │   └── ConfigurationLoader.java
│   │   ├── HttpClientPageFetcher.java
│   │   ├── JsoupLinkExtractor.java
│   │   ├── RedisFrontierQueue.java
│   │   ├── RedisVisitedRepository.java
│   │   └── ConsoleCrawlObserver.java
│   └── CrawlerApplication.java
├── test/java/                # Tests
└── resources/
    ├── application.properties
    └── logback.xml
```

### Running Tests

```bash
# Run all tests
./gradlew test

# Run only unit tests
./gradlew unitTest

# Run only integration tests
./gradlew integrationTest
```

### Integration Tests

The project includes comprehensive integration tests using Testcontainers:

- **Redis Integration**: Tests Redis-backed components with real Redis instances
- **Concurrency Testing**: Validates thread-safety under concurrent load
- **Edge Case Coverage**: Tests URI handling, encoding, and error conditions

## Key Design Decisions

### Concurrency Model

- **Virtual Threads**: Uses Java 21 virtual threads for lightweight concurrency
- **Phaser Synchronization**: Coordinates crawling completion across concurrent tasks
- **Rate Limiting**: Semaphore-based rate limiting prevents overwhelming target servers

### Data Storage

- **Redis Sets**: Uses Redis sets for O(1) visited URL lookups
- **Redis Lists**: Uses Redis lists for FIFO queue operations
- **Atomic Operations**: Leverages Redis atomic operations for thread-safe duplicate detection

### URL Processing

- **Normalization**: Standardizes URLs to prevent duplicate processing
- **Domain Filtering**: Restricts crawling to the specified domain
- **Static File Filtering**: Automatically excludes common static file types

## Monitoring and Observability

### Logging

The crawler provides detailed logging at multiple levels:

- **INFO**: High-level crawl progress and results
- **DEBUG**: Detailed processing information
- **WARN**: Non-fatal issues and retries
- **ERROR**: Fatal errors and exceptions

### Console Output

Real-time crawl progress is displayed with:
- Pages successfully crawled
- Links discovered (new vs. already seen)
- Failed crawl attempts with reasons

## Performance Characteristics

- **Memory Efficient**: Uses Redis for state storage, minimal memory footprint
- **Scalable**: Horizontal scaling possible with shared Redis instance
- **Respectful**: Built-in rate limiting and politeness policies
- **Fast**: Virtual threads enable high concurrency with low overhead

## Troubleshooting

### Common Issues

1. **Redis Connection Failed**
   ```bash
   # Check if Redis is running
   docker ps | grep redis
   
   # Start Redis if not running
   docker run -d --name redis -p 6379:6379 redis:7-alpine
   ```

2. **Out of Memory**
   ```bash
   # Increase JVM heap size
   java -Xmx2g -jar crawler.jar https://example.com
   ```

3. **Too Many Open Files**
   ```bash
   # Increase file descriptor limit
   ulimit -n 65536
   ```

### Debug Mode

Enable debug logging:

```bash
# Via system property
java -Dlogging.level.com.monzo.crawler=DEBUG -jar crawler.jar https://example.com

# Via logback configuration
# Edit src/main/resources/logback.xml and set level to DEBUG
```

### Code Style

- Follow Java naming conventions
- Use meaningful variable and method names
- Add comprehensive tests for new features
- Update documentation for API changes

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- **Jsoup**: HTML parsing and link extraction
- **Lettuce**: Redis client for Java
- **Testcontainers**: Integration testing with Docker containers
- **SLF4J/Logback**: Logging framework
