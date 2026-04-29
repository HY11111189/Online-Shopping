package com.chuwa.shopping.account.mapper;

import com.chuwa.shopping.account.dto.AccountDto;
import com.chuwa.shopping.account.dto.AddressDto;
import com.chuwa.shopping.account.dto.StoredPaymentMethodDto;
import com.chuwa.shopping.account.entity.CustomerAccount;
import com.chuwa.shopping.account.entity.CustomerAddress;
import com.chuwa.shopping.account.entity.StoredPaymentMethod;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class AccountMapper {

    public AccountDto toAccountDto(CustomerAccount account) {
        AccountDto dto = new AccountDto();
        dto.setId(account.getId());
        dto.setUsername(account.getUsername());
        dto.setFullName(account.getFullName());
        dto.setEmail(account.getEmail());
        dto.setPhoneNumber(account.getPhoneNumber());
        dto.setStatus(account.getStatus());
        dto.setMembershipLevel(account.getMembershipLevel());
        dto.setCreatedAt(account.getCreatedAt());
        dto.setUpdatedAt(account.getUpdatedAt());
        dto.setAddresses(account.getAddresses().stream().map(this::toAddressDto).collect(Collectors.toList()));
        dto.setPaymentMethods(account.getPaymentMethods().stream().map(this::toPaymentMethodDto).collect(Collectors.toList()));
        return dto;
    }

    public AddressDto toAddressDto(CustomerAddress address) {
        AddressDto dto = new AddressDto();
        dto.setId(address.getId());
        dto.setLabel(address.getLabel());
        dto.setRecipientName(address.getRecipientName());
        dto.setAddressLine1(address.getAddressLine1());
        dto.setAddressLine2(address.getAddressLine2());
        dto.setCity(address.getCity());
        dto.setState(address.getState());
        dto.setPostalCode(address.getPostalCode());
        dto.setCountry(address.getCountry());
        dto.setAddressType(address.getAddressType());
        dto.setDefaultAddress(address.isDefaultAddress());
        return dto;
    }

    public StoredPaymentMethodDto toPaymentMethodDto(StoredPaymentMethod method) {
        StoredPaymentMethodDto dto = new StoredPaymentMethodDto();
        dto.setId(method.getId());
        dto.setPaymentMethodType(method.getPaymentMethodType());
        dto.setProvider(method.getProvider());
        dto.setAccountToken(method.getAccountToken());
        dto.setMaskedNumber(method.getMaskedNumber());
        dto.setCardholderName(method.getCardholderName());
        dto.setExpiryMonth(method.getExpiryMonth());
        dto.setExpiryYear(method.getExpiryYear());
        dto.setDefaultMethod(method.isDefaultMethod());
        dto.setActive(method.isActive());
        return dto;
    }
}
