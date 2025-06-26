package com.monzo.crawler.infrastructure.config;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Utility class for WireMock configuration and common stubs used across tests
 */
public class TestWireMockConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(TestWireMockConfiguration.class);

    /**
     * Creates a WireMock server with standard configuration
     */
    public static WireMockServer createWireMockServer() {
        WireMockServer server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        String url = "http://localhost:" + server.port();
        logger.info("WireMock server started at: {}", url);
        return server;
    }

    /**
     * Gets the base URL for a WireMock server
     */
    public static String getBaseUrl(WireMockServer server) {
        return "http://localhost:" + server.port();
    }

    /**
     * Sets up basic HTML page stubs for integration testing
     */
    public static void setupBasicHtmlStubs(WireMockServer server) {
        // Simple test page
        server.stubFor(get(urlEqualTo("/test-page"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><body><h1>Test Page</h1></body></html>")));

        // 404 page
        server.stubFor(get(urlEqualTo("/not-found"))
                .willReturn(aResponse()
                        .withStatus(404)));

        // Server error page
        server.stubFor(get(urlEqualTo("/server-error"))
                .willReturn(aResponse()
                        .withStatus(500)));

        // Bad request page
        server.stubFor(get(urlEqualTo("/bad-request"))
                .willReturn(aResponse()
                        .withStatus(400)));

        // JSON content (non-HTML)
        server.stubFor(get(urlEqualTo("/json-content"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\": \"This is JSON\"}")));

        // Redirect setup
        server.stubFor(get(urlEqualTo("/redirect"))
                .willReturn(aResponse()
                        .withStatus(301)
                        .withHeader("Location", getBaseUrl(server) + "/final-destination")));

        server.stubFor(get(urlEqualTo("/final-destination"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><body>Final page</body></html>")));

        // Mixed case content type
        server.stubFor(get(urlEqualTo("/mixed-case"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "TEXT/HTML; charset=UTF-8")
                        .withBody("<html><body>Mixed case content type</body></html>")));
    }

    /**
     * Sets up comprehensive website stubs for component testing
     */
    public static void setupWebsiteStubs(WireMockServer server) {
        // Home page with links to other pages
        server.stubFor(get(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("""
                                <!DOCTYPE html>
                                <html>
                                <head><title>Home Page</title></head>
                                <body>
                                    <h1>Welcome to Test Site</h1>
                                    <nav>
                                        <a href="/about">About Us</a>
                                        <a href="/products">Products</a>
                                        <a href="/contact">Contact</a>
                                        <a href="/blog">Blog</a>
                                        <a href="mailto:test@example.com">Email Us</a>
                                        <a href="/static/image.jpg">Image</a>
                                        <a href="https://external.com">External Link</a>
                                    </nav>
                                </body>
                                </html>
                                """)));

        // About page with links back to home and to products
        server.stubFor(get(urlEqualTo("/about"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("""
                                <!DOCTYPE html>
                                <html>
                                <head><title>About Us</title></head>
                                <body>
                                    <h1>About Our Company</h1>
                                    <p>We are a test company.</p>
                                    <a href="/">Home</a>
                                    <a href="/products">Our Products</a>
                                    <a href="/team">Meet the Team</a>
                                </body>
                                </html>
                                """)));

        // Products page
        server.stubFor(get(urlEqualTo("/products"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("""
                                <!DOCTYPE html>
                                <html>
                                <head><title>Products</title></head>
                                <body>
                                    <h1>Our Products</h1>
                                    <ul>
                                        <li><a href="/products/widget1">Widget 1</a></li>
                                        <li><a href="/products/widget2">Widget 2</a></li>
                                    </ul>
                                    <a href="/">Back to Home</a>
                                </body>
                                </html>
                                """)));

        // Product detail pages
        server.stubFor(get(urlEqualTo("/products/widget1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("""
                                <!DOCTYPE html>
                                <html>
                                <head><title>Widget 1</title></head>
                                <body>
                                    <h1>Widget 1 Details</h1>
                                    <p>Amazing widget!</p>
                                    <a href="/products">Back to Products</a>
                                    <a href="/">Home</a>
                                </body>
                                </html>
                                """)));

        server.stubFor(get(urlEqualTo("/products/widget2"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("""
                                <!DOCTYPE html>
                                <html>
                                <head><title>Widget 2</title></head>
                                <body>
                                    <h1>Widget 2 Details</h1>
                                    <p>Another amazing widget!</p>
                                    <a href="/products">Back to Products</a>
                                </body>
                                </html>
                                """)));

        // Contact page
        server.stubFor(get(urlEqualTo("/contact"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("""
                                <!DOCTYPE html>
                                <html>
                                <head><title>Contact Us</title></head>
                                <body>
                                    <h1>Contact Information</h1>
                                    <p>Get in touch with us!</p>
                                    <a href="/">Home</a>
                                </body>
                                </html>
                                """)));

        // Blog page
        server.stubFor(get(urlEqualTo("/blog"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("""
                                <!DOCTYPE html>
                                <html>
                                <head><title>Blog</title></head>
                                <body>
                                    <h1>Our Blog</h1>
                                    <a href="/blog/post1">First Post</a>
                                    <a href="/">Home</a>
                                </body>
                                </html>
                                """)));

        // Blog post
        server.stubFor(get(urlEqualTo("/blog/post1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("""
                                <!DOCTYPE html>
                                <html>
                                <head><title>First Blog Post</title></head>
                                <body>
                                    <h1>Welcome to our first post</h1>
                                    <p>This is our inaugural blog post.</p>
                                    <a href="/blog">Back to Blog</a>
                                </body>
                                </html>
                                """)));

        // Team page
        server.stubFor(get(urlEqualTo("/team"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("""
                                <!DOCTYPE html>
                                <html>
                                <head><title>Our Team</title></head>
                                <body>
                                    <h1>Meet Our Team</h1>
                                    <p>Great people work here!</p>
                                    <a href="/about">About Us</a>
                                </body>
                                </html>
                                """)));
    }

    /**
     * Safely stops a WireMock server
     */
    public static void stopServer(WireMockServer server) {
        if (server != null && server.isRunning()) {
            server.stop();
            logger.debug("WireMock server stopped");
        }
    }
}
