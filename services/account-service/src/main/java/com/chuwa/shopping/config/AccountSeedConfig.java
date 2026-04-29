package com.chuwa.shopping.config;

import com.chuwa.shopping.account.dao.CustomerAccountRepository;
import com.chuwa.shopping.account.entity.AccountStatus;
import com.chuwa.shopping.account.entity.AddressType;
import com.chuwa.shopping.account.entity.CustomerAccount;
import com.chuwa.shopping.account.entity.CustomerAddress;
import com.chuwa.shopping.account.entity.MembershipLevel;
import com.chuwa.shopping.account.entity.StoredPaymentMethod;
import com.chuwa.shopping.dto.payment.PaymentMethod;
import com.chuwa.shopping.security.dao.RoleRepository;
import com.chuwa.shopping.security.dao.UserRepository;
import com.chuwa.shopping.security.entity.Role;
import com.chuwa.shopping.security.entity.User;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

@Configuration
public class AccountSeedConfig {

    @Bean
    public CommandLineRunner seedAccountData(RoleRepository roleRepository,
                                             UserRepository userRepository,
                                             CustomerAccountRepository customerAccountRepository,
                                             PasswordEncoder passwordEncoder) {
        return args -> {
            Role roleUser = roleRepository.findByName("ROLE_USER")
                    .orElseGet(() -> {
                        Role role = new Role();
                        role.setName("ROLE_USER");
                        return roleRepository.save(role);
                    });

            seedScenarioUsers(userRepository, customerAccountRepository, roleUser, passwordEncoder);
        };
    }

    private void seedScenarioUsers(UserRepository userRepository,
                                   CustomerAccountRepository customerAccountRepository,
                                   Role roleUser,
                                   PasswordEncoder passwordEncoder) {
        seedUser(userRepository, roleUser, passwordEncoder, "itest-user", "Taylor Jordan", "itest-user@example.com");
        seedAccount(customerAccountRepository, "itest-user", "Taylor Jordan", "itest-user@example.com",
                "3125551111", MembershipLevel.REGULAR, "123 Main Street", "Apt 5B", "Chicago", "IL", "60601",
                "Great Value Visa", PaymentMethod.CREDIT_CARD, "**** **** **** 4242");

        seedUser(userRepository, roleUser, passwordEncoder, "premium-user", "Avery Morgan", "premium-user@example.com");
        seedAccount(customerAccountRepository, "premium-user", "Avery Morgan", "premium-user@example.com",
                "7735552222", MembershipLevel.PREMIUM, "980 Lake Shore Drive", "Unit 17C", "Chicago", "IL", "60611",
                "Premium Mastercard", PaymentMethod.DEBIT_CARD, "**** **** **** 5454");

        for (int i = 1; i <= 10; i++) {
            String username = "load-user-" + i;
            String fullName = "Load Tester " + i;
            String email = "load-user-" + i + "@example.com";
            String phone = String.format("3125552%03d", i);
            String addressLine1 = 200 + i + " Market Street";
            String addressLine2 = "Suite " + (100 + i);
            String postalCode = String.format("606%02d", 10 + i);
            PaymentMethod paymentMethod = i % 2 == 0 ? PaymentMethod.DEBIT_CARD : PaymentMethod.CREDIT_CARD;
            String provider = paymentMethod == PaymentMethod.DEBIT_CARD ? "Load Debit" : "Load Credit";
            String maskedNumber = paymentMethod == PaymentMethod.DEBIT_CARD
                    ? "**** **** **** 55" + String.format("%02d", i)
                    : "**** **** **** 42" + String.format("%02d", i);

            seedUser(userRepository, roleUser, passwordEncoder, username, fullName, email);
            seedAccount(customerAccountRepository, username, fullName, email,
                    phone, MembershipLevel.REGULAR, addressLine1, addressLine2, "Chicago", "IL", postalCode,
                    provider, paymentMethod, maskedNumber);
        }
    }

    private void seedUser(UserRepository userRepository,
                          Role roleUser,
                          PasswordEncoder passwordEncoder,
                          String account,
                          String name,
                          String email) {
        if (userRepository.findByAccountOrEmail(account, email).isPresent()) {
            return;
        }
        User user = new User();
        user.setAccount(account);
        user.setName(name);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("Passw0rd!"));
        user.setRoles(Set.of(roleUser));
        userRepository.save(user);
    }

    private void seedAccount(CustomerAccountRepository repository,
                             String username,
                             String fullName,
                             String email,
                             String phoneNumber,
                             MembershipLevel membershipLevel,
                             String addressLine1,
                             String addressLine2,
                             String city,
                             String state,
                             String postalCode,
                             String provider,
                             PaymentMethod paymentMethod,
                             String maskedNumber) {
        if (repository.findByUsername(username).isPresent()) {
            return;
        }

        CustomerAccount account = new CustomerAccount();
        account.setUsername(username);
        account.setFullName(fullName);
        account.setEmail(email);
        account.setPassword("seeded-external-auth");
        account.setPhoneNumber(phoneNumber);
        account.setStatus(AccountStatus.ACTIVE);
        account.setMembershipLevel(membershipLevel);

        CustomerAddress address = new CustomerAddress();
        address.setLabel("Home");
        address.setRecipientName(fullName);
        address.setAddressLine1(addressLine1);
        address.setAddressLine2(addressLine2);
        address.setCity(city);
        address.setState(state);
        address.setPostalCode(postalCode);
        address.setCountry("US");
        address.setAddressType(AddressType.SHIPPING);
        address.setDefaultAddress(true);
        address.setCustomerAccount(account);
        account.getAddresses().add(address);

        StoredPaymentMethod method = new StoredPaymentMethod();
        method.setPaymentMethodType(paymentMethod);
        method.setProvider(provider);
        method.setAccountToken("tok_" + username);
        method.setMaskedNumber(maskedNumber);
        method.setCardholderName(fullName);
        method.setExpiryMonth(12);
        method.setExpiryYear(2028);
        method.setDefaultMethod(true);
        method.setActive(true);
        method.setCustomerAccount(account);
        account.getPaymentMethods().add(method);

        repository.save(account);
    }
}
