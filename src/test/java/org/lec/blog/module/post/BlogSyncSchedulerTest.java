package org.lec.blog.module.post;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.lec.blog.config.LecBlogProperties;
import org.lec.blog.module.blog.entity.MemberBlogEntity;
import org.lec.blog.module.blog.mapper.MemberBlogMapper;
import org.lec.blog.module.post.service.BlogSyncService;
import org.lec.blog.module.post.task.BlogSyncScheduler;

class BlogSyncSchedulerTest {
    @Test
    void continuesSyncingRemainingMembersWhenOneThrowsUnexpectedException() {
        var first = new MemberBlogEntity();
        first.setUserId("broken-user");
        var second = new MemberBlogEntity();
        second.setUserId("healthy-user");

        var mapper = mock(MemberBlogMapper.class);
        var sync = mock(BlogSyncService.class);
        when(mapper.selectList(any())).thenReturn(List.of(first, second));
        doThrow(new NullPointerException("boom")).when(sync).sync("broken-user");

        var props = new LecBlogProperties(null, null, new LecBlogProperties.Rss(true, 1800000L), List.of());
        new BlogSyncScheduler(props, mapper, sync).refreshActiveFeeds();

        verify(sync).sync("broken-user");
        verify(sync).sync("healthy-user");
    }
}
