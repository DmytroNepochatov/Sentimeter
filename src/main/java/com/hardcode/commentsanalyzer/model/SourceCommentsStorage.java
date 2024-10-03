package com.hardcode.commentsanalyzer.model;

import com.hardcode.commentsanalyzer.util.Util;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Component
@Getter
@Setter
public class SourceCommentsStorage {
    private Date lastUpdated;
    private Map<String, List<ProductComment>> productsCommentsMap;
    private Util util;

    @Autowired
    public SourceCommentsStorage(Util util) {
        this.util = util;
    }

    @PostConstruct
    protected void init() {
        lastUpdated = new Date();
        productsCommentsMap = util.readProductsCommentsFromCSVFile();
    }
}
