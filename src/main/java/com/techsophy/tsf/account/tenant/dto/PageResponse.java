package com.techsophy.tsf.account.tenant.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Standardized paginated list response.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public PageResponse {
        content = content == null ? List.of() : List.copyOf(content);
    }

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
