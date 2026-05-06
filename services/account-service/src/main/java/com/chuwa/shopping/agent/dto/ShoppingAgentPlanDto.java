package com.chuwa.shopping.agent.dto;

import java.util.ArrayList;
import java.util.List;

public class ShoppingAgentPlanDto {

    private String intent;
    private String reply;
    private String clarificationQuestion;
    private boolean needsClarification;
    private List<ShoppingAgentToolCallDto> planInputs = new ArrayList<>();

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

    public String getClarificationQuestion() {
        return clarificationQuestion;
    }

    public void setClarificationQuestion(String clarificationQuestion) {
        this.clarificationQuestion = clarificationQuestion;
    }

    public boolean isNeedsClarification() {
        return needsClarification;
    }

    public void setNeedsClarification(boolean needsClarification) {
        this.needsClarification = needsClarification;
    }

    public List<ShoppingAgentToolCallDto> getPlanInputs() {
        return planInputs;
    }

    public void setPlanInputs(List<ShoppingAgentToolCallDto> planInputs) {
        this.planInputs = planInputs;
    }
}