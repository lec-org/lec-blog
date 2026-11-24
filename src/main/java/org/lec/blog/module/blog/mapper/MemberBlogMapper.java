package org.lec.blog.module.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.lec.blog.module.blog.entity.MemberBlogEntity;

@Mapper
public interface MemberBlogMapper extends BaseMapper<MemberBlogEntity> {
    @Select("SELECT * FROM member_blog WHERE user_id = #{userId} FOR UPDATE")
    @Results(@Result(column = "is_captain", property = "captain"))
    MemberBlogEntity lock(String userId);
}
