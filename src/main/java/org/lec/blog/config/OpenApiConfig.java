package org.lec.blog.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@OpenAPIDefinition(info = @Info(title = "LEC 工作室成员博客 API", version = "v1"))
@SecurityScheme(name = "LecSSO", type = SecuritySchemeType.HTTP, scheme = "bearer")
public class OpenApiConfig {
    @Bean
    @Profile("(dev | test) & !prod")
    @ConditionalOnProperty(prefix = "lec.auth", name = "mock-enabled", havingValue = "true")
    OpenApiCustomizer devHeaders() {
        return api -> api.getPaths().forEach((path, item) -> {
            if (path.startsWith("/api/v1/")) {
                for (var operation : item.readOperations()) {
                    for (String header : List.of("User-Id", "Username", "Display-Name", "Avatar", "Captain")) {
                        operation.addParametersItem(new HeaderParameter().name("X-Dev-" + header)
                                .description("开发环境模拟身份").schema(new StringSchema()));
                    }
                }
            }
        });
    }
}
