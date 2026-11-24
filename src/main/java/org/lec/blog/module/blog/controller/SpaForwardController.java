package org.lec.blog.module.blog.controller;

import org.lec.blog.module.blog.service.PublicBlogService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class SpaForwardController {
    private final PublicBlogService blogs;

    public SpaForwardController(PublicBlogService blogs) {
        this.blogs = blogs;
    }

    @GetMapping({"/", "/explore", "/callback", "/write", "/write/{id}"})
    public String app() {
        return "forward:/index.html";
    }

    @GetMapping("/u/{hostedSlug}")
    public String author(@PathVariable String hostedSlug) {
        blogs.requireAuthor(hostedSlug);
        return app();
    }

    @GetMapping("/u/{hostedSlug}/p/{postSlug}")
    public String post(@PathVariable String hostedSlug, @PathVariable String postSlug) {
        blogs.requireArticle(hostedSlug, postSlug);
        return app();
    }
}
