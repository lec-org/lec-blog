package org.lec.blog.module.blog.controller;

import com.rometools.rome.feed.synd.SyndContentImpl;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndEntryImpl;
import com.rometools.rome.feed.synd.SyndFeedImpl;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedOutput;
import java.util.Date;
import java.util.List;
import org.lec.blog.common.Utc;
import org.lec.blog.module.blog.service.PublicBlogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HostedRssController {
    private final PublicBlogService blogs;

    public HostedRssController(PublicBlogService blogs) {
        this.blogs = blogs;
    }

    @GetMapping(value = "/u/{hostedSlug}/rss.xml", produces = "application/rss+xml;charset=UTF-8")
    public String rss(@PathVariable String hostedSlug) throws FeedException {
        var data = blogs.rss(hostedSlug);
        var author = data.author();
        var feed = new SyndFeedImpl();
        feed.setFeedType("rss_2.0");
        feed.setEncoding("UTF-8");
        feed.setTitle(author.blogTitle().isBlank() ? author.displayName() + " 的博客" : author.blogTitle());
        feed.setLink(author.blogUrl());
        feed.setDescription(author.bio().isBlank() ? "LEC 工作室成员博客" : author.bio());
        List<SyndEntry> entries = data.posts().stream().<SyndEntry>map(post -> {
            var entry = new SyndEntryImpl();
            entry.setUri("urn:sha256:" + post.getGuidHash());
            entry.setTitle(post.getTitle());
            entry.setLink(post.getPostUrl());
            entry.setAuthor(author.displayName());
            entry.setPublishedDate(Date.from(Utc.instant(post.getPublishedAt())));
            var description = new SyndContentImpl();
            description.setType("text/plain");
            description.setValue(post.getSummary());
            entry.setDescription(description);
            return entry;
        }).toList();
        feed.setEntries(entries);
        return new SyndFeedOutput().outputString(feed);
    }
}
