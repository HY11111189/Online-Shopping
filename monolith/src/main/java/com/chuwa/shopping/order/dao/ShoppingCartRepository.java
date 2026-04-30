package com.chuwa.shopping.order.dao;

import com.chuwa.shopping.order.entity.ShoppingCart;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ShoppingCartRepository extends CassandraRepository<ShoppingCart, Long> {

    Optional<ShoppingCart> findByCustomerId(Long customerId);
}
