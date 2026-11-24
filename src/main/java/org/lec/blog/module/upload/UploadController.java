package org.lec.blog.module.upload;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.lec.blog.auth.AuthContext;
import org.lec.blog.common.api.ApiResponse;
import org.lec.blog.common.exception.BizException;
import org.lec.blog.common.exception.ErrorCode;
import org.lec.blog.config.LecBlogProperties;
import org.lec.blog.module.blog.mapper.MemberBlogMapper;
import org.lec.blog.module.blog.service.MemberBlogService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@SecurityRequirement(name = "LecSSO")
public class UploadController {
    private static final long MAX_SIZE = 10 * 1024 * 1024;
    private final MemberBlogMapper blogs;
    private final LecBlogProperties properties;
    private final RustFsStorageClient storage;

    public UploadController(MemberBlogMapper blogs, LecBlogProperties properties, RustFsStorageClient storage) {
        this.blogs = blogs;
        this.properties = properties;
        this.storage = storage;
    }

    @PostMapping(value = "/api/v1/uploads/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<RustFsStorageClient.ImageUpload> upload(HttpServletRequest request,
            @RequestParam("file") MultipartFile file) throws IOException {
        String userId = AuthContext.user(request).userId();
        MemberBlogService.requireNotHidden(blogs.selectById(userId));
        if (file.getSize() > MAX_SIZE) {
            throw new BizException(ErrorCode.PAYLOAD_TOO_LARGE);
        }
        byte[] bytes = file.getBytes();
        var format = ImageFormat.detect(bytes);
        if (!properties.storage().enabled()) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE);
        }
        return ApiResponse.ok(storage.upload(userId, bytes, format.extension, format.mediaType));
    }

    private enum ImageFormat {
        PNG("png", "image/png"), JPEG("jpg", "image/jpeg"), GIF("gif", "image/gif"), WEBP("webp", "image/webp");

        final String extension;
        final String mediaType;

        ImageFormat(String extension, String mediaType) {
            this.extension = extension;
            this.mediaType = mediaType;
        }

        static ImageFormat detect(byte[] data) {
            if (matches(data, 0, 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)) return PNG;
            if (matches(data, 0, 0xff, 0xd8, 0xff)) return JPEG;
            if (matches(data, 0, 'G', 'I', 'F', '8', '7', 'a')
                    || matches(data, 0, 'G', 'I', 'F', '8', '9', 'a')) return GIF;
            if (matches(data, 0, 'R', 'I', 'F', 'F') && matches(data, 8, 'W', 'E', 'B', 'P')) return WEBP;
            throw new BizException(ErrorCode.BAD_REQUEST, "仅支持有效的 PNG、JPEG、GIF 或 WebP 图片");
        }

        private static boolean matches(byte[] data, int offset, int... signature) {
            if (data.length < offset + signature.length) return false;
            for (int i = 0; i < signature.length; i++) {
                if (Byte.toUnsignedInt(data[offset + i]) != signature[i]) return false;
            }
            return true;
        }
    }
}
