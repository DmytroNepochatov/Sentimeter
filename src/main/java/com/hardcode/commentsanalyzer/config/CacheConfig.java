package com.hardcode.commentsanalyzer.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    @Bean
    public CaffeineCacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();

        cacheManager.registerCustomCache("maxEntCache", Caffeine.newBuilder()
                .expireAfterWrite(20, TimeUnit.MINUTES)
                .build());

        cacheManager.registerCustomCache("naiveBayesCache", Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build());

        cacheManager.registerCustomCache("initCache", Caffeine.newBuilder()
                .expireAfterWrite(20, TimeUnit.MINUTES)
                .build());

        return cacheManager;
    }
}
