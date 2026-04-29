package com.chuwa.shopping.config.database;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackages = {
        "com.chuwa.shopping.payment.dao"
})
@EntityScan(basePackages = {
        "com.chuwa.shopping.payment.entity"
})
public class PaymentJpaConfig {
}
