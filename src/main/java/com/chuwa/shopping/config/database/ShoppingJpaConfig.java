package com.chuwa.shopping.config.database;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@EntityScan(basePackages = {
        "com.chuwa.shopping.account.entity",
        "com.chuwa.shopping.payment.entity",
        "com.chuwa.shopping.security.entity",
        "com.chuwa.shopping.shared.entity"
})
public class ShoppingJpaConfig {
}
