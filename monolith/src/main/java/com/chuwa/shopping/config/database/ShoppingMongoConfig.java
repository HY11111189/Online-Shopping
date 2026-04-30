package com.chuwa.shopping.config.database;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoAuditing
@EnableMongoRepositories(basePackages = {
        "com.chuwa.shopping.item.dao"
})
public class ShoppingMongoConfig {
}
