package com.chuwa.shopping.order.config;

import com.datastax.oss.driver.api.core.CqlSession;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrderSchemaInitializer {

    @Bean
    public CommandLineRunner orderSchemaSetup(CqlSession cqlSession) {
        return args -> cqlSession.execute(
                "CREATE TABLE IF NOT EXISTS shopping_order_number_lookup (" +
                        "order_number text PRIMARY KEY, " +
                        "customer_id bigint, " +
                        "created_at timestamp, " +
                        "order_id uuid" +
                        ")"
        );
    }
}
