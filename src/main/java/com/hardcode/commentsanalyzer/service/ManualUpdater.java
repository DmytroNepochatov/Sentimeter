package com.hardcode.commentsanalyzer.service;

import com.hardcode.commentsanalyzer.model.SourceCommentsStorage;
import com.hardcode.commentsanalyzer.util.Util;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import java.util.Date;

@Service
public class ManualUpdater {
    private SourceCommentsStorage sourceCommentsStorage;
    private CacheManager cacheManager;
    private Util util;

    @Autowired
    public ManualUpdater(SourceCommentsStorage sourceCommentsStorage, CacheManager cacheManager, Util util) {
        this.sourceCommentsStorage = sourceCommentsStorage;
        this.cacheManager = cacheManager;
        this.util = util;
    }

    public void updateSourceCommentsStorage() {
        cacheManager.getCacheNames().forEach(cacheName -> {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        });

        util.startScript();

        sourceCommentsStorage.setLastUpdated(new Date());
        sourceCommentsStorage.setProductsCommentsMap(
                util.readProductsCommentsFromCSVFile());
    }
}
