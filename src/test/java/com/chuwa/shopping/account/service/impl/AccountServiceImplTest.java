package com.chuwa.shopping.account.service.impl;

import com.chuwa.shopping.security.dao.UserRepository;
import com.chuwa.shopping.account.dao.CustomerAccountRepository;
import com.chuwa.shopping.account.dto.AccountDto;
import com.chuwa.shopping.account.dto.AccountRequestDto;
import com.chuwa.shopping.account.dto.AddressDto;
import com.chuwa.shopping.account.dto.StoredPaymentMethodDto;
import com.chuwa.shopping.account.entity.AddressType;
import com.chuwa.shopping.account.entity.CustomerAccount;
import com.chuwa.shopping.exception.ShoppingResourceNotFoundException;
import com.chuwa.shopping.payment.entity.PaymentMethod;
import com.chuwa.shopping.shared.mapper.ShoppingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceImplTest {

    @Mock
    private CustomerAccountRepository customerAccountRepository;

    @Mock
    private UserRepository userRepository;

    private AccountServiceImpl accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountServiceImpl(customerAccountRepository, userRepository, new ShoppingMapper());
    }

    @Test
    void createAccountShouldMapAddressesAndPaymentMethods() {
        when(customerAccountRepository.save(any(CustomerAccount.class))).thenAnswer(invocation -> {
            CustomerAccount account = invocation.getArgument(0);
            account.setId(1L);
            return account;
        });

        AccountRequestDto request = requestDto();

        AccountDto result = accountService.createAccount(request);

        ArgumentCaptor<CustomerAccount> captor = ArgumentCaptor.forClass(CustomerAccount.class);
        verify(customerAccountRepository).save(captor.capture());
        CustomerAccount saved = captor.getValue();
        assertEquals("alice01", saved.getUsername());
        assertEquals(1, saved.getAddresses().size());
        assertEquals(1, saved.getPaymentMethods().size());
        assertTrue(saved.getAddresses().iterator().next().getCustomerAccount() == saved);
        assertTrue(saved.getPaymentMethods().iterator().next().getCustomerAccount() == saved);
        assertEquals(1L, result.getId());
        assertEquals("alice01", result.getUsername());
    }

    @Test
    void updateAccountShouldReplaceExistingCollections() {
        CustomerAccount existing = new CustomerAccount();
        existing.setId(1L);
        when(customerAccountRepository.findDetailedById(1L)).thenReturn(Optional.of(existing));
        when(customerAccountRepository.save(any(CustomerAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccountRequestDto request = requestDto();
        request.setFullName("Alice Updated");

        AccountDto result = accountService.updateAccount(1L, request);

        assertEquals("Alice Updated", existing.getFullName());
        assertEquals(1, existing.getAddresses().size());
        assertEquals(1, existing.getPaymentMethods().size());
        assertEquals("Alice Updated", result.getFullName());
    }

    @Test
    void getAccountShouldThrowWhenMissing() {
        when(customerAccountRepository.findDetailedById(99L)).thenReturn(Optional.empty());

        assertThrows(ShoppingResourceNotFoundException.class, () -> accountService.getAccount(99L));
    }

    private AccountRequestDto requestDto() {
        AccountRequestDto request = new AccountRequestDto();
        request.setUsername("alice01");
        request.setFullName("Alice Smith");
        request.setEmail("alice@example.com");
        request.setPassword("test123");
        request.setPhoneNumber("3125551111");

        AddressDto address = new AddressDto();
        address.setLabel("home");
        address.setRecipientName("Alice Smith");
        address.setAddressLine1("123 Main St");
        address.setCity("Chicago");
        address.setState("IL");
        address.setPostalCode("60601");
        address.setCountry("US");
        address.setAddressType(AddressType.SHIPPING);
        address.setDefaultAddress(true);
        request.setAddresses(List.of(address));

        StoredPaymentMethodDto paymentMethod = new StoredPaymentMethodDto();
        paymentMethod.setPaymentMethodType(PaymentMethod.CREDIT_CARD);
        paymentMethod.setProvider("visa");
        paymentMethod.setAccountToken("tok_123");
        paymentMethod.setMaskedNumber("****1111");
        paymentMethod.setCardholderName("Alice Smith");
        paymentMethod.setExpiryMonth(12);
        paymentMethod.setExpiryYear(2030);
        paymentMethod.setDefaultMethod(true);
        paymentMethod.setActive(true);
        request.setPaymentMethods(List.of(paymentMethod));

        return request;
    }
}
