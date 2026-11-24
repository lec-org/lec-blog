package org.lec.blog.module.post.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.lec.blog.common.api.PageResult;
import org.lec.blog.module.post.dto.BlogPostItemResponse;
import org.lec.blog.module.post.mapper.BlogPostMapper;
import org.springframework.stereotype.Service;

@Service
public class PublishedPostQuery {
    private final BlogPostMapper posts;

    public PublishedPostQuery(BlogPostMapper posts) {
        this.posts = posts;
    }

    public PageResult<BlogPostItemResponse> list(String userId, int page, int size) {
        var result = posts.listActive(new Page<>(page, size), userId);
        return new PageResult<>(page, size, result.getTotal(),
                result.getRecords().stream().map(BlogPostItemResponse::from).toList());
    }
}
