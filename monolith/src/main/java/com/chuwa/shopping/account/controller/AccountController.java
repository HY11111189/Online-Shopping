package com.chuwa.shopping.account.controller;

import com.chuwa.shopping.account.dto.AccountDto;
import com.chuwa.shopping.account.dto.AccountRequestDto;
import com.chuwa.shopping.account.service.AccountService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/shopping/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<AccountDto> createAccount(@RequestBody AccountRequestDto requestDto) {
        return new ResponseEntity<>(accountService.createAccount(requestDto), HttpStatus.CREATED);
    }

    @PutMapping("/{accountId}")
    public ResponseEntity<AccountDto> updateAccount(@PathVariable Long accountId, @RequestBody AccountRequestDto requestDto) {
        return ResponseEntity.ok(accountService.updateAccount(accountId, requestDto));
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountDto> getAccount(@PathVariable Long accountId) {
        return ResponseEntity.ok(accountService.getAccount(accountId));
    }

    @GetMapping("/username/{username}")
    public ResponseEntity<AccountDto> getAccountByUsername(@PathVariable String username) {
        return ResponseEntity.ok(accountService.getAccountByUsername(username));
    }

    @GetMapping("/lookup/{identity}")
    public ResponseEntity<AccountDto> getAccountByIdentity(@PathVariable String identity) {
        return ResponseEntity.ok(accountService.getAccountByIdentity(identity));
    }

    @GetMapping("/me")
    public ResponseEntity<AccountDto> getCurrentAccount(Authentication authentication) {
        return ResponseEntity.ok(accountService.getCurrentAccount(authentication.getName()));
    }
}
