package com.hardcode.sentimeter.service;

import com.hardcode.sentimeter.model.SourceCommentsStorage;
import com.hardcode.sentimeter.util.Util;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.Date;

@Service
public class AutoUpdater {
    private SourceCommentsStorage sourceCommentsStorage;
    private Util util;

    @Autowired
    public AutoUpdater(SourceCommentsStorage sourceCommentsStorage, Util util) {
        this.sourceCommentsStorage = sourceCommentsStorage;
        this.util = util;
    }

    @Scheduled(cron = "0 0 0/4 * * *")
    public void updateSourceCommentsStorage() {
        sourceCommentsStorage.setLastUpdated(new Date());
        sourceCommentsStorage.setReadyFlag(false);
        sourceCommentsStorage.setProductsCommentsMap(
                util.readProductsCommentsFromCSVFile());
        util.init(sourceCommentsStorage.getProductsCommentsMap());
    }
}
