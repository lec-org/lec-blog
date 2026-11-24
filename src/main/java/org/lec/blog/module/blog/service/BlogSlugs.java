package org.lec.blog.module.blog.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import org.lec.blog.common.exception.BizException;
import org.lec.blog.common.exception.ErrorCode;

public final class BlogSlugs {
    private static final Set<String> RESERVED = Set.of("admin", "api", "assets", "auth", "blog", "blogs", "callback",
            "explore", "healthz", "login", "logout", "me", "new", "post", "posts", "public", "rss", "settings",
            "static", "swagger-ui", "u", "v3", "write");

    private BlogSlugs() {}

    public static String hosted(String slug) {
        if (slug.length() < 2 || slug.length() > 32 || !valid(slug) || RESERVED.contains(slug)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "博客短链须为 2–32 位小写字母、数字或单个连字符，且不能使用保留字");
        }
        return slug;
    }

    public static String post(String slug) {
        if (slug.length() > 128 || !valid(slug)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "文章短链须为 1–128 位小写字母、数字或单个连字符");
        }
        return slug;
    }

    public static String automatic(String seed, String fallback, int maxLength, Predicate<String> unavailable) {
        String base = Normalizer.normalize(seed, Normalizer.Form.NFKD).toLowerCase(Locale.ROOT)
                .replaceAll("\\p{M}+", "").replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        if (base.isBlank() || (maxLength == 32 && base.length() < 2)) {
            base = fallback;
        }
        for (int attempt = 1; ; attempt++) {
            String suffix = attempt == 1 ? "" : "-" + attempt;
            String prefix = base.substring(0, Math.min(base.length(), maxLength - suffix.length())).replaceFirst("-$", "");
            String candidate = prefix + suffix;
            if ((maxLength != 32 || !RESERVED.contains(candidate)) && !unavailable.test(candidate)) {
                return candidate;
            }
        }
    }

    private static boolean valid(String slug) {
        return slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*");
    }
}
