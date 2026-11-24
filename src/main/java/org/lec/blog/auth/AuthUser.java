package org.lec.blog.auth;

import java.util.List;

public record AuthUser(String userId, String username, String displayName, String avatar,
                       List<String> organizations, List<String> roles, boolean isStudio, boolean isCaptain) {}
