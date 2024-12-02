package com.hardcode.sentimeter.model;

import com.hardcode.sentimeter.model.dto.MyCustomEvent;
import com.hardcode.sentimeter.util.Util;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@Getter
@Setter
@Slf4j
public class SourceCommentsStorage {
    private static final String URL = "http://translator:5000";
    private static final int RETRY_DELAY = 15;
    private Date lastUpdated;
    private Map<String, List<ProductComment>> productsCommentsMap;
    private Util util;
    private boolean isReadyFlag = false;

    @Autowired
    public SourceCommentsStorage(Util util) {
        this.util = util;
    }

    @Async
    @EventListener
    public void init(MyCustomEvent event) {
        if (!isReadyFlag) {
            lastUpdated = new Date();
            productsCommentsMap = util.readProductsCommentsFromCSVFile();
            waitForService().thenRun(() -> CompletableFuture.runAsync(() -> {
                util.init(productsCommentsMap);
                log.info("Comments initialized");
                isReadyFlag = true;
            }));
        }
    }

    private CompletableFuture<Void> waitForService() {
        RestTemplate restTemplate = new RestTemplate();
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        CompletableFuture<Void> future = new CompletableFuture<>();

        executor.scheduleWithFixedDelay(() -> {
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
                    log.info("Translator is available, data pre-initialization started...");
                    future.complete(null);
                    executor.shutdown();
                }
            }
            catch (Exception e) {
                log.info("Translator not available. Waiting {} seconds to reconnect...", RETRY_DELAY);
            }
        }, 0, RETRY_DELAY, TimeUnit.SECONDS);

        return future;
    }

    public boolean isDataReady() {
        return isReadyFlag;
    }
}