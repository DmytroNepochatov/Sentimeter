package com.hardcode.commentsanalyzer.service;

import com.hardcode.commentsanalyzer.model.SourceCommentsStorage;
import com.hardcode.commentsanalyzer.util.Util;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Date;

@Service
public class ManualUpdater {
    private SourceCommentsStorage sourceCommentsStorage;

    @Autowired
    public ManualUpdater(SourceCommentsStorage sourceCommentsStorage) {
        this.sourceCommentsStorage = sourceCommentsStorage;
    }

    public void updateSourceCommentsStorage() {
        Util.startScript();

        sourceCommentsStorage.setLastUpdated(new Date());
        sourceCommentsStorage.setProductsCommentsMap(
                Util.readProductsCommentsFromCSVFile());
    }
}
