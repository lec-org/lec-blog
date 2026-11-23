package org.lec.blog.module.post.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("blog_post")
public class BlogPostEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String userId;
    private String guidHash;
    private String title;
    private String postUrl;
    private String summary;
    private String sourceType;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getGuidHash() { return guidHash; }
    public void setGuidHash(String guidHash) { this.guidHash = guidHash; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getPostUrl() { return postUrl; }
    public void setPostUrl(String postUrl) { this.postUrl = postUrl; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
