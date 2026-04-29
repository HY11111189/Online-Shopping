package com.chuwa.shopping.account.service.impl;

import com.chuwa.shopping.security.dao.UserRepository;
import com.chuwa.shopping.security.entity.User;
import com.chuwa.shopping.account.dao.CustomerAccountRepository;
import com.chuwa.shopping.account.entity.AccountStatus;
import com.chuwa.shopping.account.entity.CustomerAccount;
import com.chuwa.shopping.account.entity.CustomerAddress;
import com.chuwa.shopping.account.entity.MembershipLevel;
import com.chuwa.shopping.account.entity.StoredPaymentMethod;
import com.chuwa.shopping.exception.ShoppingResourceNotFoundException;
import com.chuwa.shopping.account.dto.AccountDto;
import com.chuwa.shopping.account.dto.AccountRequestDto;
import com.chuwa.shopping.account.dto.AddressDto;
import com.chuwa.shopping.account.dto.StoredPaymentMethodDto;
import com.chuwa.shopping.account.mapper.AccountMapper;
import com.chuwa.shopping.account.service.AccountService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

@Service
@Transactional
public class AccountServiceImpl implements AccountService {

    private final CustomerAccountRepository customerAccountRepository;
    private final UserRepository userRepository;
    private final AccountMapper accountMapper;

    public AccountServiceImpl(CustomerAccountRepository customerAccountRepository,
                              UserRepository userRepository,
                              AccountMapper accountMapper) {
        this.customerAccountRepository = customerAccountRepository;
        this.userRepository = userRepository;
        this.accountMapper = accountMapper;
    }

    @Override
    public AccountDto createAccount(AccountRequestDto requestDto) {
        CustomerAccount account = applyAccount(new CustomerAccount(), requestDto);
        return accountMapper.toAccountDto(customerAccountRepository.save(account));
    }

    @Override
    public AccountDto updateAccount(Long accountId, AccountRequestDto requestDto) {
        CustomerAccount account = getAccountEntity(accountId);
        applyAccount(account, requestDto);
        return accountMapper.toAccountDto(customerAccountRepository.save(account));
    }

    @Override
    @Transactional(readOnly = true)
    public AccountDto getAccount(Long accountId) {
        return accountMapper.toAccountDto(getAccountEntity(accountId));
    }

    @Override
    @Transactional(readOnly = true)
    public AccountDto getAccountByUsername(String username) {
        return accountMapper.toAccountDto(customerAccountRepository.findByUsername(username)
                .orElseThrow(() -> new ShoppingResourceNotFoundException("CustomerAccount", "username", username)));
    }

    @Override
    @Transactional(readOnly = true)
    public AccountDto getAccountByIdentity(String identity) {
        CustomerAccount account = customerAccountRepository.findByUsername(identity)
                .or(() -> customerAccountRepository.findByEmail(identity))
                .orElseThrow(() -> new ShoppingResourceNotFoundException("CustomerAccount", "identity", identity));
        return accountMapper.toAccountDto(account);
    }

    @Override
    @Transactional
    public AccountDto getCurrentAccount(String identity) {
        CustomerAccount account = customerAccountRepository.findByUsername(identity)
                .or(() -> customerAccountRepository.findByEmail(identity))
                .orElseGet(() -> provisionAccount(identity));
        return accountMapper.toAccountDto(account);
    }

    private CustomerAccount getAccountEntity(Long accountId) {
        return customerAccountRepository.findDetailedById(accountId)
                .orElseThrow(() -> new ShoppingResourceNotFoundException("CustomerAccount", "id", String.valueOf(accountId)));
    }

    private CustomerAccount applyAccount(CustomerAccount account, AccountRequestDto requestDto) {
        account.setUsername(requestDto.getUsername());
        account.setFullName(requestDto.getFullName());
        account.setEmail(requestDto.getEmail());
        if (requestDto.getPassword() != null && !requestDto.getPassword().isBlank()) {
            account.setPassword(requestDto.getPassword());
        }
        account.setPhoneNumber(requestDto.getPhoneNumber());
        account.setMembershipLevel(requestDto.getMembershipLevel() == null ? account.getMembershipLevel() : requestDto.getMembershipLevel());

        account.getAddresses().clear();
        for (AddressDto addressDto : requestDto.getAddresses() == null ? Collections.<AddressDto>emptyList() : requestDto.getAddresses()) {
            CustomerAddress address = new CustomerAddress();
            address.setLabel(addressDto.getLabel());
            address.setRecipientName(addressDto.getRecipientName());
            address.setAddressLine1(addressDto.getAddressLine1());
            address.setAddressLine2(addressDto.getAddressLine2());
            address.setCity(addressDto.getCity());
            address.setState(addressDto.getState());
            address.setPostalCode(addressDto.getPostalCode());
            address.setCountry(addressDto.getCountry());
            address.setAddressType(addressDto.getAddressType());
            address.setDefaultAddress(addressDto.isDefaultAddress());
            address.setCustomerAccount(account);
            account.getAddresses().add(address);
        }

        account.getPaymentMethods().clear();
        for (StoredPaymentMethodDto paymentMethodDto : requestDto.getPaymentMethods() == null ? Collections.<StoredPaymentMethodDto>emptyList() : requestDto.getPaymentMethods()) {
            StoredPaymentMethod method = new StoredPaymentMethod();
            method.setPaymentMethodType(paymentMethodDto.getPaymentMethodType());
            method.setProvider(paymentMethodDto.getProvider());
            method.setAccountToken(paymentMethodDto.getAccountToken());
            method.setMaskedNumber(paymentMethodDto.getMaskedNumber());
            method.setCardholderName(paymentMethodDto.getCardholderName());
            method.setExpiryMonth(paymentMethodDto.getExpiryMonth());
            method.setExpiryYear(paymentMethodDto.getExpiryYear());
            method.setDefaultMethod(paymentMethodDto.isDefaultMethod());
            method.setActive(paymentMethodDto.isActive());
            method.setCustomerAccount(account);
            account.getPaymentMethods().add(method);
        }

        return account;
    }

    private CustomerAccount provisionAccount(String identity) {
        User user = userRepository.findByAccountOrEmail(identity, identity)
                .orElseThrow(() -> new ShoppingResourceNotFoundException("User", "identity", identity));

        CustomerAccount account = new CustomerAccount();
        account.setUsername(user.getAccount());
        account.setFullName(user.getName() == null || user.getName().isBlank() ? user.getAccount() : user.getName());
        account.setEmail(user.getEmail());
        account.setPassword(user.getPassword());
        account.setStatus(AccountStatus.ACTIVE);
        account.setMembershipLevel(MembershipLevel.REGULAR);
        return customerAccountRepository.save(account);
    }
}
