package com.keystone.fsm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class KeystoneFsmApplication {

    public static void main(String[] args) {
        SpringApplication.run(KeystoneFsmApplication.class, args);
    }
}
