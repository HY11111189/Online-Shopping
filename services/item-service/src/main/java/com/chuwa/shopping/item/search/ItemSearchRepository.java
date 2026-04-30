package com.chuwa.shopping.item.search;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ItemSearchRepository extends ElasticsearchRepository<ItemSearchDocument, String> {
}
