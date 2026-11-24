package org.lec.blog.common.api;

import java.util.List;

public record PageResult<T>(long page, long size, long total, List<T> items) {}
