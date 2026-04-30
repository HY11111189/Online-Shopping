package com.chuwa.shopping.item.config;

import com.chuwa.shopping.item.search.ItemSearchIndexer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "shopping.search.elasticsearch.enabled", havingValue = "true", matchIfMissing = true)
public class ItemSearchConfig {

    @Bean
    public CommandLineRunner initializeItemSearch(ItemSearchIndexer itemSearchIndexer) {
        return args -> itemSearchIndexer.initializeIndex();
    }
}
