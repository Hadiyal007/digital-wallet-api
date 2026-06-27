package com.wallet.digital_wallet.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;

/**
 * Configures how Spring resolves Pageable from HTTP query parameters.
 *
 * Without a max-page-size cap, a malicious or careless client can send
 * ?size=1000000 and force your database to return a million rows in
 * one query — effectively a denial-of-service attack against your DB.
 *
 * This cap silently clamps the requested size to 50 maximum.
 * A client requesting size=200 gets size=50 with no error — this is
 * the standard behaviour (alternative: throw 400, but clamping is more
 * client-friendly and still safe).
 */
@Configuration
public class WebConfig {

    @Bean
    public PageableHandlerMethodArgumentResolverCustomizer pageableCustomizer() {
        return (PageableHandlerMethodArgumentResolver resolver) -> {
            resolver.setMaxPageSize(50);   // hard cap: ?size=200 becomes size=50
            resolver.setOneIndexedParameters(false); // pages are 0-indexed (standard)
        };
    }
}