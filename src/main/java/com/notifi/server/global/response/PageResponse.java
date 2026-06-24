package com.notifi.server.global.response;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Spring Page 직렬화를 피하고 api-spec의 페이징 응답 구조를 보장하는 래퍼.
 * <pre>
 * { "content": [...], "page": 0, "size": 20, "total_elements": 134, "total_pages": 7 }
 * </pre>
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <T> PageResponse<T> from(Page<T> springPage) {
        return new PageResponse<>(
                springPage.getContent(),
                springPage.getNumber(),
                springPage.getSize(),
                springPage.getTotalElements(),
                springPage.getTotalPages()
        );
    }
}
