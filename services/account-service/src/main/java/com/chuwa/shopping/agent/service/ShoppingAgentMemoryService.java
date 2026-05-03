package com.chuwa.shopping.agent.service;

import com.chuwa.shopping.assistant.dto.ShoppingAssistantResponseDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ShoppingAgentMemoryService {

    private static final int MAX_TURNS = 6;
    private static final Duration CACHE_TTL = Duration.ofMinutes(20);

    private final ObjectMapper objectMapper;
    private final Map<String, Deque<AgentMemoryEntry>> memoryByKey = new ConcurrentHashMap<>();

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
        Instant now = Instant.now();
        for (AgentMemoryEntry entry : turns) {
            if (entry.isExpired(now)) {
                continue;
            }
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

    public String recentSummary(String conversationKey) {
        Deque<AgentMemoryEntry> turns = memoryByKey.get(conversationKey);
        if (turns == null || turns.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        Instant now = Instant.now();
        int count = 0;
        for (AgentMemoryEntry entry : turns) {
            if (entry.isExpired(now)) {
                continue;
            }
            if (count++ >= 4) {
                break;
            }
            builder.append("- ")
                    .append(entry.getNormalizedMessage())
                    .append(" -> ")
                    .append(entry.getReply())
                    .append('\n');
        }
        return builder.toString().trim();
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
            turns.addFirst(new AgentMemoryEntry(normalizedMessage, responseJson, response.getReply(), selectedSku, selectedName, response.getOrderNumber(), Instant.now()));
            while (turns.size() > MAX_TURNS) {
                turns.removeLast();
            }
        } catch (Exception ignored) {
            // Best-effort cache only.
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

    public Optional<String> recentSelectedSku(String conversationKey) {
        Deque<AgentMemoryEntry> turns = memoryByKey.get(conversationKey);
        if (turns == null) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        for (AgentMemoryEntry entry : turns) {
            if (entry.isExpired(now)) {
                continue;
            }
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
        private final Instant createdAt;

        private AgentMemoryEntry(String normalizedMessage,
                                 String responseJson,
                                 String reply,
                                 String selectedSku,
                                 String selectedItemName,
                                 String orderNumber,
                                 Instant createdAt) {
            this.normalizedMessage = normalizedMessage;
            this.responseJson = responseJson;
            this.reply = reply == null ? "" : reply;
            this.selectedSku = selectedSku;
            this.selectedItemName = selectedItemName;
            this.orderNumber = orderNumber;
            this.createdAt = createdAt;
        }

        public String getNormalizedMessage() {
            return normalizedMessage;
        }

        public String getResponseJson() {
            return responseJson;
        }

        public String getReply() {
            return reply;
        }

        public String getSelectedSku() {
            return selectedSku;
        }

        @SuppressWarnings("unused")
        public String getSelectedItemName() {
            return selectedItemName;
        }

        @SuppressWarnings("unused")
        public String getOrderNumber() {
            return orderNumber;
        }

        public boolean isExpired(Instant now) {
            return createdAt == null || now.isAfter(createdAt.plus(CACHE_TTL));
        }
    }
}
