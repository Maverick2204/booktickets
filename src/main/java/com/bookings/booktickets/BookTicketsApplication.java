package com.bookings.booktickets;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BookTicketsApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookTicketsApplication.class, args);
    }
}
