package com.chuwa.shopping.order.dao;

import com.chuwa.shopping.order.entity.OrderKey;
import com.chuwa.shopping.order.entity.ShoppingOrder;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShoppingOrderRepository extends CassandraRepository<ShoppingOrder, OrderKey> {

    List<ShoppingOrder> findByKeyCustomerId(Long customerId);
}
