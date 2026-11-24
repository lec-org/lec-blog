package org.lec.blog.config;

import java.util.ArrayList;
import java.util.List;
import org.lec.blog.auth.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
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
        registry.addInterceptor(auth).addPathPatterns("/api/v1/**");
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
