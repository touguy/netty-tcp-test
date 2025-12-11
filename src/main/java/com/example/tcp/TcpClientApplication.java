package com.example.tcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TcpClientApplication {
    public static void main(String[] args) {
        // Spring Boot 애플리케이션을 시작합니다.
        // TcpClientRunner가 자동으로 실행됩니다.
        SpringApplication.run(TcpClientApplication.class, args);
    }
}