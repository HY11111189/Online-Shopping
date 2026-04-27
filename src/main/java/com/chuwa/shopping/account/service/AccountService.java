package com.chuwa.shopping.account.service;

import com.chuwa.shopping.account.dto.AccountDto;
import com.chuwa.shopping.account.dto.AccountRequestDto;

public interface AccountService {

    AccountDto createAccount(AccountRequestDto requestDto);

    AccountDto updateAccount(Long accountId, AccountRequestDto requestDto);

    AccountDto getAccount(Long accountId);

    AccountDto getAccountByUsername(String username);

    AccountDto getAccountByIdentity(String identity);

    AccountDto getCurrentAccount(String identity);
}
