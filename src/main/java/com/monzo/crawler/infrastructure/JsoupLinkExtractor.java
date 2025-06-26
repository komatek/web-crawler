package com.monzo.crawler.infrastructure;

import com.monzo.crawler.domain.port.out.LinkExtractor;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsoupLinkExtractor implements LinkExtractor {
    private static final Logger logger = LoggerFactory.getLogger(JsoupLinkExtractor.class);

    @Override
    public Set<URI> extractLinks(String htmlContent, URI baseUri) {
        if (htmlContent == null || htmlContent.isBlank()) {
            return Collections.emptySet();
        }
        Document doc = Jsoup.parse(htmlContent, baseUri.toString());
        return doc.select("a[href]").stream()
                .map(element -> element.attr("abs:href"))
                .filter(urlString -> !urlString.isBlank())
                .map(this::toUri)
                .filter(java.util.Objects::nonNull)
                .filter(this::isHttpOrHttps)  // Filter out non-HTTP(S) schemes
                .filter(this::isNotStaticFile)  // Filter out static files
                .collect(Collectors.toSet());
    }

    private URI toUri(String urlString) {
        try {
            return new URI(urlString.trim());
        } catch (URISyntaxException e) {
            logger.debug("Ignoring malformed URI: {}", urlString, e);
            return null;
        }
    }

    private boolean isHttpOrHttps(URI uri) {
        if (uri.getScheme() == null) return false;
        String scheme = uri.getScheme().toLowerCase();
        return scheme.equals("http") || scheme.equals("https");
    }

    private boolean isNotStaticFile(URI uri) {
        if (uri.getPath() == null) return true;
        String lowerPath = uri.getPath().toLowerCase();
        return !lowerPath.matches(".*\\.(jpg|jpeg|png|gif|bmp|webp|svg|pdf|docx?|xlsx?|pptx?|zip|rar|tar|gz|mp3|mp4|avi|mov|mkv)$");
    }
}
