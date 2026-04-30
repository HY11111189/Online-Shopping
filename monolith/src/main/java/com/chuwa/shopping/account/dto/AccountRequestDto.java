package com.chuwa.shopping.account.dto;

import java.util.ArrayList;
import java.util.List;

import com.chuwa.shopping.account.entity.MembershipLevel;

public class AccountRequestDto {

    private String username;
    private String fullName;
    private String email;
    private String password;
    private String phoneNumber;
    private MembershipLevel membershipLevel;
    private List<AddressDto> addresses = new ArrayList<>();
    private List<StoredPaymentMethodDto> paymentMethods = new ArrayList<>();

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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public MembershipLevel getMembershipLevel() {
        return membershipLevel;
    }

    public void setMembershipLevel(MembershipLevel membershipLevel) {
        this.membershipLevel = membershipLevel;
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
