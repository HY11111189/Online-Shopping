package com.chuwa.shopping.order.dao;

import com.chuwa.shopping.order.entity.OrderNumberLookup;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderNumberLookupRepository extends CassandraRepository<OrderNumberLookup, String> {
}
