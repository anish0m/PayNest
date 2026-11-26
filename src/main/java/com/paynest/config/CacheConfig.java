package com.paynest.config;

import com.paynest.user.dto.UserResponse;
import com.paynest.wallet.dto.WalletResponse;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    public static final String WALLET_BALANCE = "walletBalance";
    public static final String USER_PROFILE = "userProfile";

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    private final ObjectMapper objectMapper;
    private final Duration walletTtl;

    public CacheConfig(
            ObjectMapper objectMapper,
            @Value("${paynest.cache.wallet-ttl}") Duration walletTtl) {
        this.objectMapper = objectMapper;
        this.walletTtl = walletTtl;
    }

    @Bean
    RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(walletTtl)
                .prefixCacheNameWith("paynest:")
                .disableCachingNullValues();

        RedisCacheConfiguration walletBalanceCache = defaults.serializeValuesWith(
                SerializationPair.fromSerializer(
                        new JacksonJsonRedisSerializer<>(objectMapper, WalletResponse.class)));

        RedisCacheConfiguration userProfileCache = defaults.serializeValuesWith(
                SerializationPair.fromSerializer(
                        new JacksonJsonRedisSerializer<>(objectMapper, UserResponse.class)));

        return builder -> builder
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(Map.of(
                        WALLET_BALANCE, walletBalanceCache,
                        USER_PROFILE, userProfileCache));
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {

            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache get failed; using the database instead. cache={}, key={}",
                        cache.getName(), key, exception);
            }

            @Override
            public void handleCachePutError(
                    RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Cache put failed; a later read will use the database. cache={}, key={}",
                        cache.getName(), key, exception);
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.error("Cache eviction failed; stale data may remain until TTL. cache={}, key={}",
                        cache.getName(), key, exception);
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.error("Cache clear failed; stale data may remain until TTL. cache={}",
                        cache.getName(), exception);
            }
        };
    }
}
