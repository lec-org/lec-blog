package org.lec.blog.module.post.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import org.lec.blog.module.post.entity.BlogPostEntity;

public record NativePostDetail(@JsonUnwrapped NativePostItem post, String contentMd) {
    public static NativePostDetail from(BlogPostEntity post) {
        return new NativePostDetail(NativePostItem.from(post), post.getContentMd());
    }
}
