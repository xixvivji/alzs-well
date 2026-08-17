package com.alzswell;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AlzsWellBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(AlzsWellBackendApplication.class, args);
	}

}
