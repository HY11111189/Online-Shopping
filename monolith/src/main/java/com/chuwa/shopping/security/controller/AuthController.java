package com.chuwa.shopping.security.controller;

import com.chuwa.shopping.security.dao.RoleRepository;
import com.chuwa.shopping.security.dao.UserRepository;
import com.chuwa.shopping.security.entity.Role;
import com.chuwa.shopping.security.entity.User;
import com.chuwa.shopping.account.dao.CustomerAccountRepository;
import com.chuwa.shopping.account.entity.AccountStatus;
import com.chuwa.shopping.account.entity.AddressType;
import com.chuwa.shopping.account.entity.CustomerAddress;
import com.chuwa.shopping.account.entity.CustomerAccount;
import com.chuwa.shopping.account.entity.MembershipLevel;
import com.chuwa.shopping.security.dto.JWTAuthResponse;
import com.chuwa.shopping.security.dto.LoginDto;
import com.chuwa.shopping.security.dto.SignUpDto;
import com.chuwa.shopping.security.JwtTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;

/**
 * @author b1go
 * @date 6/26/22 5:03 PM
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private CustomerAccountRepository customerAccountRepository;

    @PostMapping("/signin")
    public ResponseEntity<JWTAuthResponse> authenticateUser(@RequestBody LoginDto loginDto) {
        Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                loginDto.getAccountOrEmail(), loginDto.getPassword()
        ));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        Jwt jwt = jwtTokenService.issueToken(authentication);

        JWTAuthResponse response = new JWTAuthResponse(jwt.getTokenValue());
        response.setTokenType("Bearer");
        response.setUsername(authentication.getName());
        response.setExpiresIn(jwt.getExpiresAt() != null && jwt.getIssuedAt() != null
                ? jwt.getExpiresAt().getEpochSecond() - jwt.getIssuedAt().getEpochSecond()
                : 0L);

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@RequestBody SignUpDto signUpDto) {
        String accountName = signUpDto.getAccount() == null ? "" : signUpDto.getAccount().trim();
        String email = signUpDto.getEmail() == null ? "" : signUpDto.getEmail().trim().toLowerCase();
        if (accountName.isBlank()) {
            return new ResponseEntity<>(Map.of("message", "Username is required"), HttpStatus.BAD_REQUEST);
        }
        if (email.isBlank()) {
            return new ResponseEntity<>(Map.of("message", "Email is required"), HttpStatus.BAD_REQUEST);
        }

        // check if username is in a DB
        if (userRepository.findByAccount(accountName).isPresent()) {
            return new ResponseEntity<>(Map.of("message", "Username is already taken!"), HttpStatus.BAD_REQUEST);
        }

        // check if email exists in DB
        if (userRepository.findByEmail(email).isPresent()) {
            return new ResponseEntity<>(Map.of("message", "Email is already taken!"), HttpStatus.BAD_REQUEST);
        }

        // create user object
        User user = new User();
        user.setName(signUpDto.getName());
        user.setAccount(accountName);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(signUpDto.getPassword()));

        Role roles = roleRepository.findByName("ROLE_USER").orElseThrow();
        user.setRoles(Collections.singleton(roles));
        userRepository.save(user);

        CustomerAccount account = new CustomerAccount();
        account.setUsername(accountName);
        account.setFullName(signUpDto.getName());
        account.setEmail(email);
        account.setPassword(user.getPassword());
        account.setPhoneNumber(signUpDto.getPhoneNumber());
        account.setStatus(AccountStatus.ACTIVE);
        account.setMembershipLevel(MembershipLevel.REGULAR);
        if (signUpDto.getAddressLine1() != null && !signUpDto.getAddressLine1().isBlank()) {
            CustomerAddress address = new CustomerAddress();
            address.setLabel("Home");
            address.setRecipientName(signUpDto.getName());
            address.setAddressLine1(signUpDto.getAddressLine1());
            address.setAddressLine2(signUpDto.getAddressLine2());
            address.setCity(signUpDto.getCity());
            address.setState(signUpDto.getState());
            address.setPostalCode(signUpDto.getPostalCode());
            address.setCountry(signUpDto.getCountry() == null || signUpDto.getCountry().isBlank() ? "US" : signUpDto.getCountry());
            address.setAddressType(AddressType.SHIPPING);
            address.setDefaultAddress(true);
            address.setCustomerAccount(account);
            account.getAddresses().add(address);
        }
        customerAccountRepository.save(account);

        Map<String, String> response = new LinkedHashMap<>();
        response.put("message", "User registered successfully");
        response.put("username", accountName);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}
