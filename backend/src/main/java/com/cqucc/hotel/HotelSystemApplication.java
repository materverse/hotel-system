package com.cqucc.hotel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 城科酒店管理系统启动类。
 */
@SpringBootApplication
@EnableTransactionManagement
@EnableScheduling
@EnableAsync
public class HotelSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(HotelSystemApplication.class, args);
    }
}
