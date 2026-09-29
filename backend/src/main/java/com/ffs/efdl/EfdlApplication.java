package com.ffs.efdl;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling // retards de validation des paiements
public class EfdlApplication {

    public static void main(String[] args) {
        SpringApplication.run(EfdlApplication.class, args);
    }
}
