package com.sam.wwol;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WwolApplication {
    public static void main(String[] args) {
        if (args.length == 1 && args[0].equals("--healthcheck")) {
            System.exit(Healthcheck.run());
            return;
        }
        SpringApplication.run(WwolApplication.class, args);
    }
}
