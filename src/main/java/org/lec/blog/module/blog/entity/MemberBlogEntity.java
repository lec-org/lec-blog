package org.lec.blog.module.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("member_blog")
public class MemberBlogEntity {
    @TableId(type = IdType.INPUT)
    private String userId;
    private String username;
    private String displayName;
    private String avatarUrl;
    @TableField("is_captain")
    private boolean captain;
    private String blogUrl;
    private String blogTitle;
    private String bio;
    private String tagsJson;
    private String rssUrl;
    private String rssEtag;
    private String rssLastModified;
    private boolean rssInitialized;
    private String lastPostTitle;
    private String lastPostUrl;
    private LocalDateTime lastPostAt;
    private LocalDateTime lastSyncedAt;
    private String syncError;
    private String status;
    private String blogMode;
    private String hostedSlug;
    private String themeJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public boolean isCaptain() { return captain; }
    public void setCaptain(boolean captain) { this.captain = captain; }

    public String getBlogUrl() { return blogUrl; }
    public void setBlogUrl(String blogUrl) { this.blogUrl = blogUrl; }

    public String getBlogTitle() { return blogTitle; }
    public void setBlogTitle(String blogTitle) { this.blogTitle = blogTitle; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public String getTagsJson() { return tagsJson; }
    public void setTagsJson(String tagsJson) { this.tagsJson = tagsJson; }

    public String getRssUrl() { return rssUrl; }
    public void setRssUrl(String rssUrl) { this.rssUrl = rssUrl; }

    public String getRssEtag() { return rssEtag; }
    public void setRssEtag(String rssEtag) { this.rssEtag = rssEtag; }

    public String getRssLastModified() { return rssLastModified; }
    public void setRssLastModified(String rssLastModified) { this.rssLastModified = rssLastModified; }

    public boolean isRssInitialized() { return rssInitialized; }
    public void setRssInitialized(boolean rssInitialized) { this.rssInitialized = rssInitialized; }

    public String getLastPostTitle() { return lastPostTitle; }
    public void setLastPostTitle(String lastPostTitle) { this.lastPostTitle = lastPostTitle; }

    public String getLastPostUrl() { return lastPostUrl; }
    public void setLastPostUrl(String lastPostUrl) { this.lastPostUrl = lastPostUrl; }

    public LocalDateTime getLastPostAt() { return lastPostAt; }
    public void setLastPostAt(LocalDateTime lastPostAt) { this.lastPostAt = lastPostAt; }

    public LocalDateTime getLastSyncedAt() { return lastSyncedAt; }
    public void setLastSyncedAt(LocalDateTime lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }

    public String getSyncError() { return syncError; }
    public void setSyncError(String syncError) { this.syncError = syncError; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getBlogMode() { return blogMode; }
    public void setBlogMode(String blogMode) { this.blogMode = blogMode; }

    public String getHostedSlug() { return hostedSlug; }
    public void setHostedSlug(String hostedSlug) { this.hostedSlug = hostedSlug; }

    public String getThemeJson() { return themeJson; }
    public void setThemeJson(String themeJson) { this.themeJson = themeJson; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
