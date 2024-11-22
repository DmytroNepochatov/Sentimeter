package com.hardcode.sentimeter.service;

import com.hardcode.sentimeter.model.SourceCommentsStorage;
import com.hardcode.sentimeter.util.Util;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Date;

@Service
public class ManualUpdater {
    private SourceCommentsStorage sourceCommentsStorage;
    private Util util;

    @Autowired
    public ManualUpdater(SourceCommentsStorage sourceCommentsStorage, Util util) {
        this.sourceCommentsStorage = sourceCommentsStorage;
        this.util = util;
    }

    public void updateSourceCommentsStorage() {
        util.startScript();

        sourceCommentsStorage.setLastUpdated(new Date());
        sourceCommentsStorage.setProductsCommentsMap(
                util.readProductsCommentsFromCSVFile());
        util.init(sourceCommentsStorage.getProductsCommentsMap());
    }
}
