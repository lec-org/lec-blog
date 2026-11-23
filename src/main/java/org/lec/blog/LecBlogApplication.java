package org.lec.blog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LecBlogApplication {
    public static void main(String[] args) {
        SpringApplication.run(LecBlogApplication.class, args);
    }
}
