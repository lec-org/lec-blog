package org.lec.blog.module.post;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.lec.blog.config.LecBlogProperties;
import org.lec.blog.module.blog.mapper.MemberBlogMapper;
import org.lec.blog.module.post.service.BlogSyncService;
import org.lec.blog.module.post.task.BlogSyncScheduler;

class BlogSyncSchedulerTest {
    @Test
    void continuesSyncingRemainingMembersWhenOneThrowsUnexpectedException() {
        var mapper = mock(MemberBlogMapper.class);
        var sync = mock(BlogSyncService.class);
        when(mapper.listExternalFeedOwners()).thenReturn(List.of("broken-user", "healthy-user"));
        doThrow(new NullPointerException("boom")).when(sync).sync("broken-user");

        var props = new LecBlogProperties(null, null, new LecBlogProperties.Rss(true, 1800000L), List.of(),
                "http://localhost:8080", null);
        new BlogSyncScheduler(props, mapper, sync).refreshActiveFeeds();

        verify(sync).sync("broken-user");
        verify(sync).sync("healthy-user");
    }
}
