package org.lec.blog.module.post.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import org.jsoup.Jsoup;
import org.lec.blog.common.exception.BizException;
import org.lec.blog.common.security.SafeHttpClient;
import org.lec.blog.common.security.SafeUrlValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RssDiscoveryService {
    private static final Logger LOG = LoggerFactory.getLogger(RssDiscoveryService.class);
    private final SafeHttpClient http;
    private final SafeUrlValidator urls;

    public RssDiscoveryService(SafeHttpClient http, SafeUrlValidator urls) {
        this.http = http;
        this.urls = urls;
    }

    public Optional<String> discover(String blogUrl) {
        try {
            var response = http.get(blogUrl, null, null);
            var html = Jsoup.parse(new ByteArrayInputStream(response.body()), null, response.url().toString());
            for (var link : html.select("head link[rel][type][href]")) {
                String rel = link.attr("rel").toLowerCase(Locale.ROOT);
                String type = link.attr("type").toLowerCase(Locale.ROOT).split(";", 2)[0].strip();
                if (!(" " + rel.replaceAll("\\s+", " ") + " ").contains(" alternate ")
                        || !(type.equals("application/rss+xml") || type.equals("application/atom+xml"))) {
                    continue;
                }
                try {
                    return Optional.of(urls.validate(link.absUrl("href")).toString());
                } catch (BizException ignored) {
                    // The page may advertise another valid feed.
                }
            }
        } catch (IOException | BizException exception) {
            LOG.debug("博客首页未完成订阅源探测", exception);
        }
        return Optional.empty();
    }
}
