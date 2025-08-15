package com.aegis.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AegisDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(AegisDemoApplication.class, args);
    }
}
