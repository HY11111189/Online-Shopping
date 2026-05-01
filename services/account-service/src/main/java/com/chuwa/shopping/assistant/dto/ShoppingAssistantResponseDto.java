package com.chuwa.shopping.assistant.dto;

import java.util.ArrayList;
import java.util.List;

public class ShoppingAssistantResponseDto {

    private String intent;
    private String reply;
    private boolean requiresSignIn;
    private String orderNumber;
    private String checkoutUrl;
    private List<ShoppingAssistantItemDto> items = new ArrayList<>();
    private List<ShoppingAssistantActionDto> actions = new ArrayList<>();

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
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

    public String getCheckoutUrl() {
        return checkoutUrl;
    }

    public void setCheckoutUrl(String checkoutUrl) {
        this.checkoutUrl = checkoutUrl;
    }

    public List<ShoppingAssistantItemDto> getItems() {
        return items;
    }

    public void setItems(List<ShoppingAssistantItemDto> items) {
        this.items = items;
    }

    public List<ShoppingAssistantActionDto> getActions() {
        return actions;
    }

    public void setActions(List<ShoppingAssistantActionDto> actions) {
        this.actions = actions;
    }
}
