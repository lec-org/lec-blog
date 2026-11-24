package org.lec.blog.module.post.task;

import org.lec.blog.config.LecBlogProperties;
import org.lec.blog.module.blog.mapper.MemberBlogMapper;
import org.lec.blog.module.post.service.BlogSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BlogSyncScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(BlogSyncScheduler.class);
    private final LecBlogProperties properties;
    private final MemberBlogMapper blogs;
    private final BlogSyncService sync;

    public BlogSyncScheduler(LecBlogProperties properties, MemberBlogMapper blogs, BlogSyncService sync) {
        this.properties = properties;
        this.blogs = blogs;
        this.sync = sync;
    }

    @Scheduled(fixedDelayString = "${lec.rss.interval-ms}", initialDelayString = "${lec.rss.interval-ms}")
    public void refreshActiveFeeds() {
        if (!properties.rss().syncEnabled()) {
            return;
        }
        for (String userId : blogs.listExternalFeedOwners()) {
            try {
                sync.sync(userId);
            } catch (Exception exception) {
                LOG.warn("同步订阅源失败，继续处理后续成员，作者 ID {}", userId, exception);
            }
        }
    }
}
