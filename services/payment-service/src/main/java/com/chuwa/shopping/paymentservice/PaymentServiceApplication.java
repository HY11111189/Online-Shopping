package com.chuwa.shopping.paymentservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {
        "com.chuwa.shopping.payment",
        "com.chuwa.shopping.exception"
})
@EnableFeignClients(basePackages = "com.chuwa.shopping.client")
@EnableJpaRepositories(basePackages = "com.chuwa.shopping.payment.dao")
@EntityScan(basePackages = "com.chuwa.shopping.payment.entity")
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
