package com.chuwa.shopping.assistant.dto;

import java.util.ArrayList;
import java.util.List;

public class ShoppingAssistantResponseDto {

    private String intent;
    private String state;
    private String reply;
    private String resolvedQuery;
    private boolean requiresSignIn;
    private String orderNumber;
    private String orderStatus;
    private String checkoutUrl;
    private Integer cartItemCount;
    private List<ShoppingAssistantItemDto> items = new ArrayList<>();
    private List<ShoppingAssistantOrderDto> orders = new ArrayList<>();
    private List<ShoppingAssistantActionDto> actions = new ArrayList<>();

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    public String getResolvedQuery() {
        return resolvedQuery;
    }

    public void setResolvedQuery(String resolvedQuery) {
        this.resolvedQuery = resolvedQuery;
    }

    public boolean isRequiresSignIn() {
        return requiresSignIn;
    }

    public void setRequiresSignIn(boolean requiresSignIn) {
        this.requiresSignIn = requiresSignIn;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
    }

    public String getCheckoutUrl() {
        return checkoutUrl;
    }

    public void setCheckoutUrl(String checkoutUrl) {
        this.checkoutUrl = checkoutUrl;
    }

    public Integer getCartItemCount() {
        return cartItemCount;
    }

    public void setCartItemCount(Integer cartItemCount) {
        this.cartItemCount = cartItemCount;
    }

    public List<ShoppingAssistantItemDto> getItems() {
        return items;
    }

    public void setItems(List<ShoppingAssistantItemDto> items) {
        this.items = items;
    }

    public List<ShoppingAssistantOrderDto> getOrders() {
        return orders;
    }

    public void setOrders(List<ShoppingAssistantOrderDto> orders) {
        this.orders = orders;
    }

    public List<ShoppingAssistantActionDto> getActions() {
        return actions;
    }

    public void setActions(List<ShoppingAssistantActionDto> actions) {
        this.actions = actions;
    }
}
