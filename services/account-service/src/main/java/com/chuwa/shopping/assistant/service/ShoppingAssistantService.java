package com.chuwa.shopping.assistant.service;

import com.chuwa.shopping.account.dto.AccountDto;
import com.chuwa.shopping.account.dto.AddressDto;
import com.chuwa.shopping.account.dto.StoredPaymentMethodDto;
import com.chuwa.shopping.account.entity.MembershipLevel;
import com.chuwa.shopping.account.service.AccountService;
import com.chuwa.shopping.assistant.dto.AssistantIntentDto;
import com.chuwa.shopping.assistant.dto.ShoppingAssistantActionDto;
import com.chuwa.shopping.assistant.dto.ShoppingAssistantItemDto;
import com.chuwa.shopping.assistant.dto.ShoppingAssistantRequestDto;
import com.chuwa.shopping.assistant.dto.ShoppingAssistantResponseDto;
import com.chuwa.shopping.dto.item.ItemDto;
import com.chuwa.shopping.dto.order.AddressSnapshotDto;
import com.chuwa.shopping.dto.order.OrderDto;
import com.chuwa.shopping.dto.payment.PaymentMethod;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class ShoppingAssistantService {

    private static final Logger log = LoggerFactory.getLogger(ShoppingAssistantService.class);
    private static final String DEFAULT_OPENAI_MODEL = "gpt-5.1";

    private static final List<String> SUPPORTED_INTENTS = List.of(
            "SEARCH_PRODUCTS",
            "ADD_TO_CART",
            "PLACE_ORDER",
            "GENERAL_HELP"
    );

    private final AccountService accountService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${shopping.services.item.base-url}")
    private String itemServiceBaseUrl;

    @Value("${shopping.services.order.base-url}")
    private String orderServiceBaseUrl;

    @Value("${shopping.assistant.openai.api-key:}")
    private String openAiApiKey;

    @Value("${shopping.assistant.openai.model:gpt-5.1}")
    private String openAiModel;

    public ShoppingAssistantService(AccountService accountService, ObjectMapper objectMapper) {
        this.accountService = accountService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public ShoppingAssistantResponseDto chat(ShoppingAssistantRequestDto requestDto, Authentication authentication) {
        String userMessage = requestDto == null || requestDto.getMessage() == null ? "" : requestDto.getMessage().trim();
        ShoppingAssistantResponseDto emptyResponse = new ShoppingAssistantResponseDto();
        if (userMessage.isBlank()) {
            emptyResponse.setIntent("GENERAL_HELP");
            emptyResponse.setReply("Tell me what you want to find, add, or order.");
            return emptyResponse;
        }

        AssistantIntentDto intent = classifyIntent(userMessage, authentication);
        String resolvedIntent = normalizeIntent(intent.getIntent(), userMessage);

        if ("PLACE_ORDER".equals(resolvedIntent)) {
            return placeOrder(authentication);
        }
        if ("ADD_TO_CART".equals(resolvedIntent)) {
            return addToCart(intent, userMessage, authentication);
        }
        if ("SEARCH_PRODUCTS".equals(resolvedIntent)) {
            return searchProducts(intent, userMessage);
        }
        return generalHelp(userMessage, intent);
    }

    private AssistantIntentDto classifyIntent(String userMessage, Authentication authentication) {
        if (openAiApiKey == null || openAiApiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY is not configured for shopping assistant classification");
        }
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", resolveOpenAiModel());
        payload.put("max_output_tokens", 180);
        payload.put("input", userMessage);
        payload.put("instructions", buildAssistantInstructions(authentication));

        ObjectNode text = payload.putObject("text");
        ObjectNode format = text.putObject("format");
        format.put("type", "json_schema");
        format.put("name", "shopping_assistant_intent");
        format.put("strict", true);

        ObjectNode schema = format.putObject("schema");
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        ObjectNode intentSchema = properties.putObject("intent");
        intentSchema.put("type", "string");
        intentSchema.putArray("enum")
                .add("SEARCH_PRODUCTS")
                .add("ADD_TO_CART")
                .add("PLACE_ORDER")
                .add("GENERAL_HELP");
        properties.putObject("query").put("type", "string");
        properties.putObject("sku").put("type", "string");
        properties.putObject("category").put("type", "string");
        properties.putObject("quantity").put("type", "integer");

        schema.putArray("required")
                .add("intent")
                .add("query")
                .add("sku")
                .add("category")
                .add("quantity");
        schema.put("additionalProperties", false);

        try {
            JsonNode response = postJsonToJson("https://api.openai.com/v1/responses", payload, openAiApiKey);
            String textOutput = extractOutputText(response);
            if (textOutput == null || textOutput.isBlank()) {
                throw new IllegalStateException("OpenAI assistant classification returned an empty response");
            }
            AssistantIntentDto intent = objectMapper.readValue(textOutput, AssistantIntentDto.class);
            if (intent.getIntent() == null || intent.getIntent().isBlank()) {
                throw new IllegalStateException("OpenAI assistant classification returned no intent");
            }
            return intent;
        } catch (Exception ex) {
            throw new IllegalStateException("OpenAI assistant classification failed: " + rootCauseMessage(ex), ex);
        }
    }

    private String resolveOpenAiModel() {
        String configuredModel = openAiModel == null ? "" : openAiModel.trim();
        if (configuredModel.isBlank() || "gpt-5.5".equalsIgnoreCase(configuredModel)) {
            if (!configuredModel.isBlank()) {
                log.warn("Ignoring unsupported OpenAI model '{}', using '{}'", configuredModel, DEFAULT_OPENAI_MODEL);
            }
            return DEFAULT_OPENAI_MODEL;
        }
        return configuredModel;
    }

    private String buildAssistantInstructions(Authentication authentication) {
        String userContext = isAuthenticated(authentication)
                ? "The shopper is signed in and may ask you to search products, add items to cart, or place an order."
                : "The shopper is not signed in. Search and browse requests are allowed, but cart and order actions require sign-in.";
        return "You are a shopping assistant for an online store. " + userContext + " "
                + "Return only JSON that matches the schema. "
                + "Use intent SEARCH_PRODUCTS for browsing or lookup requests, ADD_TO_CART for cart requests, PLACE_ORDER for checkout/order placement, and GENERAL_HELP for everything else. "
                + "Use empty strings for unknown text fields. Use quantity 1 when not specified. "
                + "If the user mentions a product name, put the best search terms in query. "
                + "If the user mentions a SKU, put it in sku.";
    }

    private ShoppingAssistantResponseDto searchProducts(AssistantIntentDto intent, String fallbackQuery) {
        String query = firstNonBlank(intent.getQuery(), fallbackQuery);
        List<ItemDto> matches = searchItems(query, intent.getCategory(), 6);
        ShoppingAssistantResponseDto response = new ShoppingAssistantResponseDto();
        response.setIntent("SEARCH_PRODUCTS");
        if (matches.isEmpty()) {
            response.setReply("I could not find matching products for \"" + query + "\". Try a different search term or category.");
            response.getActions().add(action("Browse home", "/index.html", "navigate"));
            return response;
        }
        response.setReply("I found " + matches.size() + " product" + (matches.size() == 1 ? "" : "s") + " for \"" + query + "\".");
        response.setItems(matches.stream().map(this::toAssistantItem).collect(Collectors.toList()));
        response.getActions().add(action("Open search results", "/index.html?q=" + urlEncode(query), "navigate"));
        response.getActions().add(action("View cart", "/cart.html", "navigate"));
        return response;
    }

    private ShoppingAssistantResponseDto addToCart(AssistantIntentDto intent, String fallbackQuery, Authentication authentication) {
        ShoppingAssistantResponseDto response = new ShoppingAssistantResponseDto();
        response.setIntent("ADD_TO_CART");
        if (!isAuthenticated(authentication)) {
            response.setRequiresSignIn(true);
            response.setReply("Sign in first and I can add items to your cart.");
            response.getActions().add(action("Sign in", "/signin.html", "navigate"));
            return response;
        }

        String token = bearerToken(authentication);
        Long customerId = currentCustomerId(authentication);
        if (token == null || customerId == null) {
            response.setRequiresSignIn(true);
            response.setReply("I need your signed-in session to add items to the cart.");
            return response;
        }

        String sku = firstNonBlank(intent.getSku(), findBestSku(intent.getQuery(), fallbackQuery));
        if (sku.isBlank()) {
            response.setReply("Tell me which product you want added, or search by name and I will narrow it down.");
            return response;
        }

        ItemDto item = getItemBySku(sku).orElseGet(() -> searchItems(firstNonBlank(intent.getQuery(), fallbackQuery), null, 1).stream().findFirst().orElse(null));
        if (item == null) {
            response.setReply("I could not find that product to add it to your cart.");
            return response;
        }

        int quantity = intent.getQuantity() == null || intent.getQuantity() < 1 ? 1 : intent.getQuantity();
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("itemId", item.getSku() + "::SHIPPING");
        payload.put("sku", item.getSku());
        payload.put("quantity", quantity);
        try {
            sendJson(orderServiceUrl("/api/v1/shopping/carts/" + customerId + "/items"), payload, token);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to add item to cart", ex);
        }

        response.setReply("I added " + quantity + " x " + item.getItemName() + " to your cart.");
        response.getItems().add(toAssistantItem(item));
        response.getActions().add(action("Go to cart", "/cart.html", "navigate"));
        response.getActions().add(action("Keep shopping", "/index.html", "navigate"));
        return response;
    }

    private ShoppingAssistantResponseDto placeOrder(Authentication authentication) {
        ShoppingAssistantResponseDto response = new ShoppingAssistantResponseDto();
        response.setIntent("PLACE_ORDER");
        if (!isAuthenticated(authentication)) {
            response.setRequiresSignIn(true);
            response.setReply("Sign in first so I can place an order for you.");
            response.getActions().add(action("Sign in", "/signin.html", "navigate"));
            return response;
        }

        String token = bearerToken(authentication);
        Long customerId = currentCustomerId(authentication);
        if (token == null || customerId == null) {
            response.setRequiresSignIn(true);
            response.setReply("I need your signed-in session before I can place an order.");
            return response;
        }

        AccountDto account = accountService.getCurrentAccount(authentication.getName());
        CartSnapshot cart = loadCart(customerId, token);
        if (cart.items.isEmpty()) {
            response.setReply("Your cart is empty. Add a few items and I can place the order.");
            response.getActions().add(action("Browse products", "/index.html", "navigate"));
            return response;
        }

        AddressSnapshotDto shippingAddress = selectAddress(account);
        if (shippingAddress == null) {
            response.setReply("I found items in your cart, but you need a saved shipping address before I can place the order.");
            response.getActions().add(action("Update account", "/account.html", "navigate"));
            return response;
        }

        StoredPaymentMethodDto paymentMethod = selectPaymentMethod(account);
        if (paymentMethod == null) {
            response.setReply("I found items in your cart, but you need a saved payment method before I can place the order.");
            response.getActions().add(action("Update account", "/account.html", "navigate"));
            return response;
        }

        BigDecimal subtotal = cart.items.stream()
                .map(item -> item.getLineTotal() == null && item.getUnitPrice() != null && item.getQuantity() != null
                        ? item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()))
                        : defaultMoney(item.getLineTotal()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal shippingAmount = calculateShippingAmount(account, cart.items, subtotal);

        ObjectNode request = objectMapper.createObjectNode();
        request.put("customerId", customerId);
        request.put("currencyCode", firstNonBlank(cart.currencyCode, "USD"));
        request.put("taxAmount", "0.00");
        request.put("shippingAmount", shippingAmount.toPlainString());
        request.put("discountAmount", "0.00");
        request.put("createRequestId", "assistant-order-" + System.currentTimeMillis());
        request.putPOJO("shippingAddress", shippingAddress);
        request.putPOJO("billingAddress", shippingAddress);
        request.putPOJO("paymentMethod", paymentMethod.getPaymentMethodType() == null ? PaymentMethod.CREDIT_CARD : paymentMethod.getPaymentMethodType());

        ArrayNode items = request.putArray("items");
        for (CartItemSnapshot cartItem : cart.items) {
            ObjectNode item = items.addObject();
            item.put("itemId", cartItem.getItemId());
            item.put("sku", cartItem.getSku());
            item.put("itemName", cartItem.getItemName());
            item.put("upc", cartItem.getUpc());
            item.put("quantity", cartItem.getQuantity());
            item.put("unitPrice", cartItem.getUnitPrice() == null ? "0.00" : cartItem.getUnitPrice().toPlainString());
            item.put("lineTotal", cartItem.getLineTotal() == null
                    ? defaultMoney(cartItem.getUnitPrice()).multiply(BigDecimal.valueOf(cartItem.getQuantity())).toPlainString()
                    : cartItem.getLineTotal().toPlainString());
        }

        OrderDto order;
        try {
            order = readValue(sendJson(orderServiceUrl("/api/v1/shopping/orders"), request, token), OrderDto.class);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to place order", ex);
        }
        clearCart(customerId, token, cart.items);

        response.setReply("Your order was placed successfully.");
        response.setOrderNumber(order.getOrderNumber());
        response.setCheckoutUrl("/order-status.html?orderNumber=" + urlEncode(order.getOrderNumber()));
        response.getActions().add(action("View order", response.getCheckoutUrl(), "navigate"));
        response.getActions().add(action("Continue shopping", "/index.html", "navigate"));
        return response;
    }

    private ShoppingAssistantResponseDto generalHelp(String userMessage, AssistantIntentDto intent) {
        ShoppingAssistantResponseDto response = new ShoppingAssistantResponseDto();
        response.setIntent("GENERAL_HELP");
        response.setReply("I can search products, add items to your cart, and place an order if you are signed in.");
        String query = firstNonBlank(intent.getQuery(), userMessage);
        List<ItemDto> matches = searchItems(query, null, 4);
        if (!matches.isEmpty()) {
            response.setItems(matches.stream().map(this::toAssistantItem).collect(Collectors.toList()));
            response.getActions().add(action("See results", "/index.html?q=" + urlEncode(query), "navigate"));
        }
        response.getActions().add(action("Browse home", "/index.html", "navigate"));
        return response;
    }

    private List<ItemDto> searchItems(String query, String category, int limit) {
        try {
            StringBuilder url = new StringBuilder(itemServiceUrl("/api/v1/shopping/items/search"));
            url.append("?limit=").append(limit);
            if (query != null && !query.isBlank()) {
                url.append("&q=").append(urlEncode(query));
            }
            if (category != null && !category.isBlank()) {
                url.append("&category=").append(urlEncode(category));
            }
            JsonNode node = readJson(sendRequest(url.toString(), null, "GET"));
            return objectMapper.readValue(node.toString(), new TypeReference<List<ItemDto>>() {});
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }

    private Optional<ItemDto> getItemBySku(String sku) {
        if (sku == null || sku.isBlank()) return Optional.empty();
        try {
            JsonNode node = readJson(sendRequest(itemServiceUrl("/api/v1/shopping/items/sku/" + urlEncode(sku)), null, "GET"));
            return Optional.of(objectMapper.treeToValue(node, ItemDto.class));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private CartSnapshot loadCart(Long customerId, String token) {
        try {
            JsonNode node = readJson(sendRequest(orderServiceUrl("/api/v1/shopping/carts/" + customerId), token, "GET"));
            CartSnapshot snapshot = new CartSnapshot();
            snapshot.currencyCode = "USD";
            JsonNode items = node.path("items");
            if (items.isArray()) {
                for (JsonNode itemNode : items) {
                    snapshot.items.add(toCartItemSnapshot(itemNode));
                }
            }
            return snapshot;
        } catch (Exception ex) {
            return new CartSnapshot();
        }
    }

    private void clearCart(Long customerId, String token, List<CartItemSnapshot> items) {
        for (CartItemSnapshot item : items) {
            try {
                sendRequest(orderServiceUrl("/api/v1/shopping/carts/" + customerId + "/items/" + urlEncode(item.getItemId())), token, "DELETE");
            } catch (Exception ignored) {
                // Ignore cleanup failures. Order creation already succeeded.
            }
        }
        try {
            sendRequest(orderServiceUrl("/api/v1/shopping/carts/" + customerId + "/checkout"), token, "POST");
        } catch (Exception ignored) {
            // Best-effort only.
        }
    }

    private AddressSnapshotDto selectAddress(AccountDto account) {
        if (account == null || account.getAddresses() == null || account.getAddresses().isEmpty()) {
            return null;
        }
        AddressDto address = account.getAddresses().stream()
                .sorted(Comparator.comparing(AddressDto::isDefaultAddress).reversed())
                .findFirst()
                .orElse(null);
        if (address == null) {
            return null;
        }
        AddressSnapshotDto snapshot = new AddressSnapshotDto();
        snapshot.setRecipientName(address.getRecipientName());
        snapshot.setAddressLine1(address.getAddressLine1());
        snapshot.setAddressLine2(address.getAddressLine2());
        snapshot.setCity(address.getCity());
        snapshot.setState(address.getState());
        snapshot.setPostalCode(address.getPostalCode());
        snapshot.setCountry(address.getCountry());
        snapshot.setPhoneNumber(account.getPhoneNumber());
        return snapshot;
    }

    private StoredPaymentMethodDto selectPaymentMethod(AccountDto account) {
        if (account == null || account.getPaymentMethods() == null || account.getPaymentMethods().isEmpty()) {
            return null;
        }
        return account.getPaymentMethods().stream()
                .filter(StoredPaymentMethodDto::isActive)
                .sorted(Comparator.comparing(StoredPaymentMethodDto::isDefaultMethod).reversed())
                .findFirst()
                .orElse(null);
    }

    private BigDecimal calculateShippingAmount(AccountDto account, List<CartItemSnapshot> items, BigDecimal subtotal) {
        boolean hasShippingItems = items.stream().anyMatch(item -> item.getItemId() != null && item.getItemId().endsWith("::SHIPPING"));
        if (!hasShippingItems) {
            return BigDecimal.ZERO;
        }
        boolean premium = account != null && account.getMembershipLevel() == MembershipLevel.PREMIUM;
        if (premium || subtotal.compareTo(new BigDecimal("35.00")) >= 0) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal("6.00");
    }

    private ShoppingAssistantItemDto toAssistantItem(ItemDto item) {
        ShoppingAssistantItemDto assistantItem = new ShoppingAssistantItemDto();
        assistantItem.setSku(item.getSku());
        assistantItem.setItemName(item.getItemName());
        assistantItem.setBrand(item.getBrand());
        assistantItem.setCategory(item.getCategory());
        assistantItem.setUnitPrice(item.getUnitPrice());
        assistantItem.setCurrencyCode(firstNonBlank(item.getCurrencyCode(), "USD"));
        assistantItem.setDiscountPercent(item.getDiscountPercent());
        assistantItem.setImageUrl(item.getPictureUrls() == null || item.getPictureUrls().isEmpty() ? null : item.getPictureUrls().get(0));
        assistantItem.setProductUrl("/product.html?sku=" + urlEncode(item.getSku()));
        assistantItem.setReason(item.getDescription());
        return assistantItem;
    }

    private ShoppingAssistantActionDto action(String label, String href, String type) {
        ShoppingAssistantActionDto action = new ShoppingAssistantActionDto();
        action.setLabel(label);
        action.setHref(href);
        action.setType(type);
        return action;
    }

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && !"anonymousUser".equalsIgnoreCase(authentication.getName());
    }

    private String bearerToken(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken) {
            return ((JwtAuthenticationToken) authentication).getToken().getTokenValue();
        }
        return null;
    }

    private Long currentCustomerId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        try {
            AccountDto account = accountService.getCurrentAccount(authentication.getName());
            return account.getId();
        } catch (Exception ex) {
            return null;
        }
    }

    private String normalizeIntent(String intent, String userMessage) {
        if (intent == null || intent.isBlank()) {
            throw new IllegalStateException("OpenAI assistant classification returned no intent");
        }
        String normalized = intent.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_INTENTS.contains(normalized)) {
            throw new IllegalStateException("OpenAI assistant returned unsupported intent: " + intent);
        }
        return normalized;
    }

    private String buildOpenAiErrorMessage(HttpResponse<String> response) {
        return "OpenAI request failed with status " + response.statusCode();
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        String message = null;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                message = current.getMessage();
            }
            current = current.getCause();
        }
        return message == null ? "unknown error" : message;
    }

    private JsonNode postJsonToJson(String url, Object body, String bearerToken) {
        try {
            return readJson(sendJson(url, body, bearerToken));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to call " + url, ex);
        }
    }

    private String sendJson(String url, Object body, String bearerToken) throws IOException {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header(HttpHeaders.ACCEPT, "application/json")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        String authorizationToken = url.startsWith("https://api.openai.com")
                ? openAiApiKey
                : bearerToken;
        if (authorizationToken != null && !authorizationToken.isBlank()) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + authorizationToken);
        }
        try {
            HttpResponse<String> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException(buildOpenAiErrorMessage(response) + ": " + response.body());
            }
            return response.body();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to call " + url, ex);
        }
    }

    private String sendRequest(String url, String bearerToken, String method) throws IOException {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header(HttpHeaders.ACCEPT, "application/json");
        if (bearerToken != null && !bearerToken.isBlank()) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
        }
        try {
            if ("POST".equalsIgnoreCase(method)) {
                request.POST(HttpRequest.BodyPublishers.noBody());
            } else if ("DELETE".equalsIgnoreCase(method)) {
                request.DELETE();
            } else if ("PUT".equalsIgnoreCase(method)) {
                request.PUT(HttpRequest.BodyPublishers.noBody());
            } else {
                request.GET();
            }
            HttpResponse<String> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException(method + " " + url + " failed: " + response.statusCode() + " " + response.body());
            }
            return response.body();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to call " + url, ex);
        }
    }

    private JsonNode readJson(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to parse response", ex);
        }
    }

    private <T> T readValue(String body, Class<T> type) {
        try {
            return objectMapper.readValue(body, type);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to parse response", ex);
        }
    }

    private String extractOutputText(JsonNode response) {
        JsonNode output = response.path("output");
        if (!output.isArray()) {
            return null;
        }
        for (JsonNode item : output) {
            if (!"message".equals(item.path("type").asText())) {
                continue;
            }
            JsonNode content = item.path("content");
            if (!content.isArray()) {
                continue;
            }
            for (JsonNode part : content) {
                String type = part.path("type").asText();
                if ("output_text".equals(type) || "text".equals(type)) {
                    return part.path("text").asText();
                }
            }
        }
        return null;
    }

    private String itemServiceUrl(String path) {
        return normalizeBaseUrl(itemServiceBaseUrl) + path;
    }

    private String orderServiceUrl(String path) {
        return normalizeBaseUrl(orderServiceBaseUrl) + path;
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String firstNonBlank(String first, String fallback) {
        return first != null && !first.isBlank() ? first : fallback == null ? "" : fallback;
    }

    private BigDecimal defaultMoney(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private String findBestSku(String query, String fallbackQuery) {
        String effectiveQuery = firstNonBlank(query, fallbackQuery);
        if (effectiveQuery.isBlank()) {
            return "";
        }
        return searchItems(effectiveQuery, null, 1).stream()
                .map(ItemDto::getSku)
                .filter(sku -> sku != null && !sku.isBlank())
                .findFirst()
                .orElse("");
    }

    private CartItemSnapshot toCartItemSnapshot(JsonNode node) {
        CartItemSnapshot snapshot = new CartItemSnapshot();
        snapshot.setItemId(node.path("itemId").asText(""));
        snapshot.setSku(node.path("sku").asText(""));
        snapshot.setItemName(node.path("itemName").asText(""));
        snapshot.setUpc(node.path("upc").asText(""));
        snapshot.setQuantity(node.path("quantity").asInt(1));
        if (node.hasNonNull("unitPrice")) {
            snapshot.setUnitPrice(node.path("unitPrice").decimalValue());
        }
        if (node.hasNonNull("lineTotal")) {
            snapshot.setLineTotal(node.path("lineTotal").decimalValue());
        }
        return snapshot;
    }

    private static class CartSnapshot {
        private String currencyCode = "USD";
        private final List<CartItemSnapshot> items = new ArrayList<>();
    }

    private static class CartItemSnapshot {
        private String itemId;
        private String sku;
        private String itemName;
        private String upc;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal lineTotal;

        public String getItemId() {
            return itemId;
        }

        public void setItemId(String itemId) {
            this.itemId = itemId;
        }

        public String getSku() {
            return sku;
        }

        public void setSku(String sku) {
            this.sku = sku;
        }

        public String getItemName() {
            return itemName;
        }

        public void setItemName(String itemName) {
            this.itemName = itemName;
        }

        public String getUpc() {
            return upc;
        }

        public void setUpc(String upc) {
            this.upc = upc;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }

        public BigDecimal getUnitPrice() {
            return unitPrice;
        }

        public void setUnitPrice(BigDecimal unitPrice) {
            this.unitPrice = unitPrice;
        }

        public BigDecimal getLineTotal() {
            return lineTotal;
        }

        public void setLineTotal(BigDecimal lineTotal) {
            this.lineTotal = lineTotal;
        }
    }
}
