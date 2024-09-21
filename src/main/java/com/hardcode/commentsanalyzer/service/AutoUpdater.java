package com.hardcode.commentsanalyzer.service;

import com.hardcode.commentsanalyzer.model.SourceCommentsStorage;
import com.hardcode.commentsanalyzer.util.Util;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.Date;

@Service
public class AutoUpdater {
    private SourceCommentsStorage sourceCommentsStorage;

    @Autowired
    public AutoUpdater(SourceCommentsStorage sourceCommentsStorage) {
        this.sourceCommentsStorage = sourceCommentsStorage;
    }

    @Scheduled(cron = "0 0 0/4 * * *")
    public void updateSourceCommentsStorage() {
        Util.startScript();

        sourceCommentsStorage.setLastUpdated(new Date());
        sourceCommentsStorage.setProductsCommentsMap(
                Util.readProductsCommentsFromCSVFile());
    }
}
