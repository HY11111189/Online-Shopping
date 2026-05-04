package com.chuwa.shopping.account.controller;

import com.chuwa.shopping.account.dto.AccountDto;
import com.chuwa.shopping.account.service.AccountService;
import com.chuwa.shopping.dto.account.AccountProfileDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/api/v1/shopping/accounts")
public class InternalAccountController {

    private final AccountService accountService;

    public InternalAccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountProfileDto> getAccount(@PathVariable Long accountId) {
        AccountDto account = accountService.getAccount(accountId);
        AccountProfileDto response = new AccountProfileDto();
        response.setId(account.getId());
        response.setMembershipLevel(account.getMembershipLevel() == null ? null : account.getMembershipLevel().name());
        return ResponseEntity.ok(response);
    }
}
