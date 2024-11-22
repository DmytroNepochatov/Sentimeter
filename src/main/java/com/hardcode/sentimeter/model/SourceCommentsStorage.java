package com.hardcode.sentimeter.model;

import com.hardcode.sentimeter.util.Util;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import javax.annotation.PostConstruct;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Component
@Getter
@Setter
@Slf4j
public class SourceCommentsStorage {
    private static final String URL = "http://translator:5000";
    private static final int RETRY_DELAY = 5;
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

        waitForService();
        util.init(productsCommentsMap);
    }

    private void waitForService() {
        RestTemplate restTemplate = new RestTemplate();
        boolean flag = false;

        while (!flag) {
            try {
                HttpHeaders headers = new HttpHeaders();
                HttpEntity<String> entity = new HttpEntity<>(headers);

                ResponseEntity<String> response = restTemplate.exchange(
                        URL,
                        HttpMethod.HEAD,
                        entity,
                        String.class
                );

                if (response.getStatusCode() == HttpStatus.OK) {
                    flag = true;
                    log.info("Translator is available, data pre-initialization started...");
                }
            }
            catch (Exception e) {
                log.info("Translator not available. Waiting {} seconds to reconnect...", RETRY_DELAY);

                try {
                    Thread.sleep(RETRY_DELAY * 1000);
                }
                catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
