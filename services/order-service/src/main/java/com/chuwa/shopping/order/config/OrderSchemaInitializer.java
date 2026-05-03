package com.chuwa.shopping.order.config;

import com.datastax.oss.driver.api.core.CqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrderSchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(OrderSchemaInitializer.class);
    private static final String KEYSPACE = "online_shopping_order";
    private static final String ORDERS_TABLE = "shopping_orders";
    private static final String PAYMENT_METHOD_COLUMN = "payment_method";

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

    @Bean
    public CommandLineRunner shoppingOrderSchemaSetup(CqlSession cqlSession) {
        return args -> {
            try {
                // Keep the live table compatible with the current ShoppingOrder entity.
                if (!tableExists(cqlSession, KEYSPACE, ORDERS_TABLE)) {
                    log.warn("Skipping shopping_orders migration because the table does not exist yet.");
                    return;
                }
                if (columnExists(cqlSession, KEYSPACE, ORDERS_TABLE, PAYMENT_METHOD_COLUMN)) {
                    return;
                }
                cqlSession.execute("ALTER TABLE " + ORDERS_TABLE + " ADD payment_method text");
            } catch (Exception ex) {
                log.warn("Skipping shopping_orders payment_method migration: {}", ex.getMessage());
            }
        };
    }

    private boolean tableExists(CqlSession cqlSession, String keyspace, String tableName) {
        return cqlSession.execute(
                        "SELECT table_name FROM system_schema.tables WHERE keyspace_name = '" + keyspace + "' AND table_name = '" + tableName + "'")
                .one() != null;
    }

    private boolean columnExists(CqlSession cqlSession, String keyspace, String tableName, String columnName) {
        return cqlSession.execute(
                        "SELECT column_name FROM system_schema.columns WHERE keyspace_name = '" + keyspace + "' AND table_name = '" + tableName + "' AND column_name = '" + columnName + "'")
                .one() != null;
    }
}
