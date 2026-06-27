package com.wallet.digital_wallet.dto;

import lombok.Getter;
import org.springframework.data.domain.Page;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Clean pagination wrapper for API responses.
 *
 * Why not return Spring's Page<T> directly?
 * Spring's Page serializes with internal fields (pageable, sort, empty,
 * numberOfElements...) that are Spring-specific and verbose. This wrapper
 * exposes only what the client needs: the content slice + enough metadata
 * to render "Page 3 of 47" and drive next/prev buttons.
 *
 * Generic: PagedResponse<TransactionResponse>, PagedResponse<UserResponse>, etc.
 *
 * Two constructors:
 * - from(Page<T>)            → when the entity type IS the response type
 * - from(Page<T>, Function)  → when you need to map entity → DTO before wrapping
 */
@Getter
public class PagedResponse<T> {

    private final List<T> content;
    private final int pageNumber;    // zero-based (page 0 = first page)
    private final int pageSize;      // records per page
    private final long totalElements; // total records across all pages
    private final int totalPages;    // ceil(totalElements / pageSize)
    private final boolean first;     // is this the first page?
    private final boolean last;      // is this the last page?

    private PagedResponse(Page<?> page, List<T> mappedContent) {
        this.content       = mappedContent;
        this.pageNumber    = page.getNumber();
        this.pageSize      = page.getSize();
        this.totalElements = page.getTotalElements();
        this.totalPages    = page.getTotalPages();
        this.first         = page.isFirst();
        this.last          = page.isLast();
    }

    /**
     * Use when no mapping is needed — entity type is directly returned.
     * Example: PagedResponse.from(transactionPage)
     */
    @SuppressWarnings("unchecked")
    public static <T> PagedResponse<T> from(Page<T> page) {
        return new PagedResponse<>(page, page.getContent());
    }

    /**
     * Use when you need to convert entities to DTOs before wrapping.
     * Example: PagedResponse.from(userPage, UserResponse::from)
     */
    public static <E, T> PagedResponse<T> from(Page<E> page, Function<E, T> mapper) {
        List<T> mapped = page.getContent()
                .stream()
                .map(mapper)
                .collect(Collectors.toList());
        return new PagedResponse<>(page, mapped);
    }
}