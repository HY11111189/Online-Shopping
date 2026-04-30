package com.chuwa.shopping.config.database;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.cassandra.repository.config.EnableCassandraRepositories;

@Configuration
@EnableCassandraRepositories(basePackages = {
        "com.chuwa.shopping.order.dao"
})
public class ShoppingCassandraConfig {
}
