package com.scholarship;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ScholarshipApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScholarshipApplication.class, args);
    }
}
