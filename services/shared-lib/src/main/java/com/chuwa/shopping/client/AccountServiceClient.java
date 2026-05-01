package com.chuwa.shopping.client;

import com.chuwa.shopping.dto.account.AccountProfileDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "account-service-client", url = "${shopping.services.account.base-url}")
public interface AccountServiceClient {

    @GetMapping("/internal/api/v1/shopping/accounts/{accountId}")
    AccountProfileDto getAccount(@PathVariable("accountId") Long accountId);
}
