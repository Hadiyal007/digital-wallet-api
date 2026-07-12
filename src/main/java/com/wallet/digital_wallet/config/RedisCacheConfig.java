package com.wallet.digital_wallet.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

/**
 * Configures Spring's cache abstraction to use Redis as the backing
 * store, with two deliberate choices beyond the Spring Boot default:
 *
 * 1. JSON serialization (GenericJackson2JsonRedisSerializer) instead of
 *    the default JDK serialization. JDK serialization would require
 *    every cached DTO (e.g. AdminDashboardResponse) to implement
 *    Serializable, and produces an opaque binary blob in Redis that's
 *    unreadable if you inspect it with redis-cli. JSON is readable,
 *    debuggable, and needs no changes to the DTOs being cached.
 *
 * 2. A CacheErrorHandler that LOGS instead of throwing. By default, if
 *    Redis is down or unreachable, Spring's cache abstraction lets the
 *    connection exception propagate - meaning a Redis outage would take
 *    the admin dashboard down too, even though the underlying database
 *    query would have worked fine. That's a bad tradeoff: caching is
 *    supposed to be a performance optimization, not a new point of
 *    failure. With this handler, a cache get/put failure is logged and
 *    the call simply falls through to the real method (cache miss
 *    behaviour) - so the dashboard keeps working, just uncached, until
 *    Redis comes back.
 */
@Configuration
@EnableCaching
@Slf4j
public class RedisCacheConfig {

    @Value("${app.cache.dashboard-ttl-seconds:60}")
    private long dashboardTtlSeconds;

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        PolymorphicTypeValidator ptv = LaissezFaireSubTypeValidator.instance;
        mapper.activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.NON_FINAL);
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(mapper);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(dashboardTtlSeconds))
                .disableCachingNullValues()
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(serializer));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .build();
    }

    @Bean
    public CacheErrorHandler cacheErrorHandler() {
        return new SimpleCacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, org.springframework.cache.Cache cache, Object key) {
                log.warn("Cache GET failed for cache '{}', key '{}' — falling back to the database: {}",
                        cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, org.springframework.cache.Cache cache, Object key, Object value) {
                log.warn("Cache PUT failed for cache '{}', key '{}' — result was NOT cached: {}",
                        cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, org.springframework.cache.Cache cache, Object key) {
                log.warn("Cache EVICT failed for cache '{}', key '{}': {}",
                        cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, org.springframework.cache.Cache cache) {
                log.warn("Cache CLEAR failed for cache '{}': {}", cache.getName(), exception.getMessage());
            }
        };
    }
}
