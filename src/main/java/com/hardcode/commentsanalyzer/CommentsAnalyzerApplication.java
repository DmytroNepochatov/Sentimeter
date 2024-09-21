package com.hardcode.commentsanalyzer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CommentsAnalyzerApplication {
    public static void main(String[] args) {
        SpringApplication.run(CommentsAnalyzerApplication.class, args);
    }
}