package com.chuwa.shopping.account.dao;

import com.chuwa.shopping.account.entity.CustomerAccount;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerAccountRepository extends JpaRepository<CustomerAccount, Long> {

    @EntityGraph(attributePaths = {"addresses", "paymentMethods"})
    Optional<CustomerAccount> findDetailedById(Long id);

    @EntityGraph(attributePaths = {"addresses", "paymentMethods"})
    Optional<CustomerAccount> findByEmail(String email);

    @EntityGraph(attributePaths = {"addresses", "paymentMethods"})
    Optional<CustomerAccount> findByUsername(String username);
}
