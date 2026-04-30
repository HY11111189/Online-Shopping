package com.chuwa.shopping.account.dto;

import com.chuwa.shopping.account.entity.AccountStatus;
import com.chuwa.shopping.account.entity.MembershipLevel;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AccountDto {

    private Long id;
    private String username;
    private String fullName;
    private String email;
    private String phoneNumber;
    private AccountStatus status;
    private MembershipLevel membershipLevel;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<AddressDto> addresses = new ArrayList<>();
    private List<StoredPaymentMethodDto> paymentMethods = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    public MembershipLevel getMembershipLevel() {
        return membershipLevel;
    }

    public void setMembershipLevel(MembershipLevel membershipLevel) {
        this.membershipLevel = membershipLevel;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<AddressDto> getAddresses() {
        return addresses;
    }

    public void setAddresses(List<AddressDto> addresses) {
        this.addresses = addresses;
    }

    public List<StoredPaymentMethodDto> getPaymentMethods() {
        return paymentMethods;
    }

    public void setPaymentMethods(List<StoredPaymentMethodDto> paymentMethods) {
        this.paymentMethods = paymentMethods;
    }
}
