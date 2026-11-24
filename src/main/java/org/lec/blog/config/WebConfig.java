package org.lec.blog.config;

import java.util.ArrayList;
import java.util.List;
import java.time.Duration;
import org.lec.blog.auth.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final AuthInterceptor auth;
    private final LecBlogProperties properties;

    public WebConfig(AuthInterceptor auth, LecBlogProperties properties) {
        this.auth = auth;
        this.properties = properties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(auth).addPathPatterns("/api/v1/**").excludePathPatterns("/api/v1/public/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**").addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
        registry.addResourceHandler("/index.html").addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noCache());
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origins = new ArrayList<>();
        for (String origin : properties.corsAllowedOrigins()) {
            if (origin.startsWith("*.")) {
                origins.add("https://" + origin);
            } else if (!origin.isBlank()) {
                origins.add(origin);
            }
        }
        registry.addMapping("/api/v1/**").allowedOriginPatterns(origins.toArray(String[]::new))
                .allowedMethods("GET", "PUT", "DELETE", "POST", "OPTIONS").allowedHeaders("*");
    }
}
