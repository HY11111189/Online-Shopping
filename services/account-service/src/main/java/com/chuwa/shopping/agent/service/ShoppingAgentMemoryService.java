package com.chuwa.shopping.agent.service;

import com.chuwa.shopping.assistant.dto.ShoppingAssistantResponseDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ShoppingAgentMemoryService {

    // Only the last 6 non-clarification turns are kept per conversation.
    // Clarification exchanges are intentionally excluded from the cache — they
    // are captured in the AI-generated rolling summary instead.
    private static final int MAX_TURNS = 6;

    private final ObjectMapper objectMapper;
    private final Map<String, Deque<AgentMemoryEntry>> memoryByKey = new ConcurrentHashMap<>();
    // One AI-generated summary string per conversation key.
    // Updated by ShoppingAgentService after every turn (including clarifications).
    private final Map<String, String> summaryByKey = new ConcurrentHashMap<>();

    public ShoppingAgentMemoryService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<ShoppingAssistantResponseDto> findCachedResponse(String conversationKey, String normalizedMessage) {
        if (conversationKey == null || conversationKey.isBlank() || normalizedMessage == null || normalizedMessage.isBlank()) {
            return Optional.empty();
        }
        Deque<AgentMemoryEntry> turns = memoryByKey.get(conversationKey);
        if (turns == null) {
            return Optional.empty();
        }
        for (AgentMemoryEntry entry : turns) {
            if (entry.getNormalizedMessage().equals(normalizedMessage)) {
                try {
                    return Optional.of(objectMapper.readValue(entry.getResponseJson(), ShoppingAssistantResponseDto.class));
                } catch (Exception ignored) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    // Returns the AI-generated rolling summary for this conversation.
    // ShoppingAgentService passes this into the OpenAI planning prompt so the
    // model has full context even across multiple clarification rounds.
    public String recentSummary(String conversationKey) {
        return summaryByKey.getOrDefault(conversationKey, "");
    }

    // Called by ShoppingAgentService after every turn with the summary OpenAI produced.
    public void storeSummary(String conversationKey, String summary) {
        if (conversationKey == null || conversationKey.isBlank() || summary == null || summary.isBlank()) {
            return;
        }
        summaryByKey.put(conversationKey, summary);
    }

    // Clears the rolling summary after a completed round so it does not bleed
    // into the next independent question.
    public void clearSummary(String conversationKey) {
        summaryByKey.remove(conversationKey);
    }

    public void remember(String conversationKey, String userMessage, ShoppingAssistantResponseDto response) {
        if (conversationKey == null || conversationKey.isBlank() || response == null) {
            return;
        }
        String normalizedMessage = normalize(userMessage);
        String selectedSku = response.getItems() != null && response.getItems().size() == 1
                ? response.getItems().get(0).getSku()
                : null;
        String selectedName = response.getItems() != null && response.getItems().size() == 1
                ? response.getItems().get(0).getItemName()
                : null;
        try {
            String responseJson = objectMapper.writeValueAsString(response);
            Deque<AgentMemoryEntry> turns = memoryByKey.computeIfAbsent(conversationKey, key -> new ArrayDeque<>());
            turns.addFirst(new AgentMemoryEntry(normalizedMessage, responseJson, response.getReply(),
                    selectedSku, selectedName, response.getOrderNumber()));
            while (turns.size() > MAX_TURNS) {
                turns.removeLast();
            }
        } catch (Exception ignored) {
        }
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Punct}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    // Used by runAddToCartPlan / runPlaceOrderPlan to recall the last product
    // the user interacted with, enabling follow-ups like "buy that one".
    public Optional<String> recentSelectedSku(String conversationKey) {
        Deque<AgentMemoryEntry> turns = memoryByKey.get(conversationKey);
        if (turns == null) {
            return Optional.empty();
        }
        for (AgentMemoryEntry entry : turns) {
            if (entry.getSelectedSku() != null && !entry.getSelectedSku().isBlank()) {
                return Optional.of(entry.getSelectedSku());
            }
        }
        return Optional.empty();
    }

    private static final class AgentMemoryEntry {
        private final String normalizedMessage;
        private final String responseJson;
        private final String reply;
        private final String selectedSku;
        private final String selectedItemName;
        private final String orderNumber;

        private AgentMemoryEntry(String normalizedMessage,
                                 String responseJson,
                                 String reply,
                                 String selectedSku,
                                 String selectedItemName,
                                 String orderNumber) {
            this.normalizedMessage = normalizedMessage;
            this.responseJson = responseJson;
            this.reply = reply == null ? "" : reply;
            this.selectedSku = selectedSku;
            this.selectedItemName = selectedItemName;
            this.orderNumber = orderNumber;
        }

        public String getNormalizedMessage() { return normalizedMessage; }
        public String getResponseJson() { return responseJson; }
        public String getReply() { return reply; }
        public String getSelectedSku() { return selectedSku; }

        @SuppressWarnings("unused")
        public String getSelectedItemName() { return selectedItemName; }

        @SuppressWarnings("unused")
        public String getOrderNumber() { return orderNumber; }
    }
}