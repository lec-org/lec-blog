package org.lec.blog.module.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.lec.blog.module.blog.entity.MemberBlogEntity;

@Mapper
public interface MemberBlogMapper extends BaseMapper<MemberBlogEntity> {
    @Select("SELECT * FROM member_blog WHERE user_id = #{userId} FOR UPDATE")
    @Results(@Result(column = "is_captain", property = "captain"))
    MemberBlogEntity lock(String userId);

    @Insert("""
            INSERT INTO blog_member_joined (user_id)
            SELECT #{userId}
            WHERE NOT EXISTS (SELECT 1 FROM blog_member_joined WHERE user_id = #{userId})
            """)
    int markFirstJoin(@Param("userId") String userId);

    @Select("SELECT user_id FROM member_blog WHERE status = 'ACTIVE' AND blog_mode = 'EXTERNAL' AND rss_url IS NOT NULL")
    List<String> listExternalFeedOwners();
}
