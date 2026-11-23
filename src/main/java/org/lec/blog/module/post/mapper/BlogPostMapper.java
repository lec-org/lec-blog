package org.lec.blog.module.post.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
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
                   b.username, b.display_name, b.avatar_url, b.blog_title
            FROM blog_post p JOIN member_blog b ON p.user_id = b.user_id
            WHERE b.status = 'ACTIVE'
            <if test="userId != null">AND p.user_id = #{userId}</if>
            ORDER BY p.published_at DESC, p.id DESC
            </script>
            """)
    IPage<BlogPostRow> listActive(IPage<BlogPostRow> page, @Param("userId") String userId);
}
