package org.lec.blog.module.post.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.lec.blog.module.post.entity.BlogPostEntity;
import org.lec.blog.module.post.dto.BlogPostRow;

@Mapper
public interface BlogPostMapper extends BaseMapper<BlogPostEntity> {
    @Select("""
            <script>
            SELECT p.id, p.user_id, p.title, p.post_url, p.summary, p.source_type, p.published_at,
                   b.username, b.display_name, b.avatar_url, b.blog_title, b.hosted_slug,
                   p.slug, p.cover_url, COALESCE(CHAR_LENGTH(p.content_md), 0) AS content_length
            FROM blog_post p JOIN member_blog b ON p.user_id = b.user_id
            WHERE b.status = 'ACTIVE'
              AND p.post_status = 'PUBLISHED'
              AND ((b.blog_mode = 'EXTERNAL' AND p.source_type = 'RSS')
                OR (b.blog_mode = 'HOSTED' AND p.source_type = 'NATIVE'))
            <if test="userId != null">AND p.user_id = #{userId}</if>
            ORDER BY p.published_at DESC, p.id DESC
            </script>
            """)
    IPage<BlogPostRow> listActive(IPage<BlogPostRow> page, @Param("userId") String userId);

    static LambdaQueryWrapper<BlogPostEntity> publishedFor(String userId, String sourceType) {
        return Wrappers.<BlogPostEntity>lambdaQuery().eq(BlogPostEntity::getUserId, userId)
                .eq(BlogPostEntity::getSourceType, sourceType)
                .eq(BlogPostEntity::getPostStatus, "PUBLISHED");
    }

    default BlogPostEntity latestPublished(String userId, String sourceType) {
        return selectOne(publishedFor(userId, sourceType)
                .select(BlogPostEntity::getTitle, BlogPostEntity::getPostUrl, BlogPostEntity::getPublishedAt)
                .orderByDesc(BlogPostEntity::getPublishedAt, BlogPostEntity::getId).last("LIMIT 1"));
    }
}
