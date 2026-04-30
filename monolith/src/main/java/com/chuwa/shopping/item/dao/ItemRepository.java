package com.chuwa.shopping.item.dao;

import com.chuwa.shopping.item.entity.ItemDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ItemRepository extends MongoRepository<ItemDocument, String> {

    Optional<ItemDocument> findBySku(String sku);

    Optional<ItemDocument> findByUpc(String upc);
}
