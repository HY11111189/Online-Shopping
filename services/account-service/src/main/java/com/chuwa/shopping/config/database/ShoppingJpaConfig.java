package com.chuwa.shopping.config.database;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableJpaRepositories(basePackages = {
        "com.chuwa.shopping.account.dao",
        "com.chuwa.shopping.security.dao"
})
@EntityScan(basePackages = {
        "com.chuwa.shopping.account.entity",
        "com.chuwa.shopping.security.entity"
})
public class ShoppingJpaConfig {
}
