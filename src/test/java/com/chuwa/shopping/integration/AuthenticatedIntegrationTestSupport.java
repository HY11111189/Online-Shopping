package com.chuwa.shopping.integration;

import com.chuwa.shopping.security.dao.RoleRepository;
import com.chuwa.shopping.security.dao.UserRepository;
import com.chuwa.shopping.security.entity.Role;
import com.chuwa.shopping.security.entity.User;
import com.chuwa.shopping.security.dto.JWTAuthResponse;
import com.chuwa.shopping.security.dto.LoginDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

abstract class AuthenticatedIntegrationTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    protected String bearerToken;

    @BeforeEach
    void authenticateTestUser() throws Exception {
        ensureRoleExists("ROLE_USER");
        ensureUserExists("itest-user", "itest@example.com", "Passw0rd!");
        bearerToken = issueToken("itest-user", "Passw0rd!");
    }

    protected String authHeader() {
        return "Bearer " + bearerToken;
    }

    private void ensureRoleExists(String roleName) {
        roleRepository.findByName(roleName).orElseGet(() -> {
            Role role = new Role();
            role.setName(roleName);
            return roleRepository.save(role);
        });
    }

    private void ensureUserExists(String account, String email, String rawPassword) {
        userRepository.findByAccount(account).orElseGet(() -> {
            Role userRole = roleRepository.findByName("ROLE_USER").orElseThrow();
            User user = new User();
            user.setName("Integration Test User");
            user.setAccount(account);
            user.setEmail(email);
            user.setPassword(passwordEncoder.encode(rawPassword));
            user.setRoles(Collections.singleton(userRole));
            return userRepository.save(user);
        });
    }

    protected String issueToken(String accountOrEmail, String password) throws Exception {
        LoginDto loginDto = new LoginDto();
        loginDto.setAccountOrEmail(accountOrEmail);
        loginDto.setPassword(password);

        String response = mockMvc.perform(post("/api/v1/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDto)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JWTAuthResponse authResponse = objectMapper.readValue(response, JWTAuthResponse.class);
        return authResponse.getAccessToken();
    }
}
