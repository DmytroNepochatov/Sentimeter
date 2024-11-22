package com.hardcode.sentimeter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Sentimeter {
    public static void main(String[] args) {
        SpringApplication.run(Sentimeter.class, args);
    }
}