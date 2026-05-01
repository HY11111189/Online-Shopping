package com.chuwa.shopping.assistant.service;

import com.chuwa.shopping.account.dto.AccountDto;
import com.chuwa.shopping.account.dto.AddressDto;
import com.chuwa.shopping.account.dto.StoredPaymentMethodDto;
import com.chuwa.shopping.account.entity.MembershipLevel;
import com.chuwa.shopping.account.service.AccountService;
import com.chuwa.shopping.assistant.dto.AssistantIntentDto;
import com.chuwa.shopping.assistant.dto.ShoppingAssistantActionDto;
import com.chuwa.shopping.assistant.dto.ShoppingAssistantItemDto;
import com.chuwa.shopping.assistant.dto.ShoppingAssistantOrderDto;
import com.chuwa.shopping.assistant.dto.ShoppingAssistantRequestDto;
import com.chuwa.shopping.assistant.dto.ShoppingAssistantResponseDto;
import com.chuwa.shopping.dto.item.ItemDto;
import com.chuwa.shopping.dto.order.AddressSnapshotDto;
import com.chuwa.shopping.dto.order.OrderDto;
import com.chuwa.shopping.dto.order.OrderStatus;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
            "LOOKUP_ORDERS",
            "GENERAL_HELP"
    );
    private static final Pattern ORDER_NUMBER_PATTERN = Pattern.compile("(ORD-[A-Z0-9]{8,})", Pattern.CASE_INSENSITIVE);
    private static final Pattern DAY_WINDOW_PATTERN = Pattern.compile("(?:within|last|past)\\s+(\\d+)\\s+day", Pattern.CASE_INSENSITIVE);

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
            emptyResponse.setState("idle");
            emptyResponse.setReply("Ask me to browse products, add something to your cart, place an order, or look up an order.");
            emptyResponse.getActions().add(action("Browse all products", "/index.html", "navigate"));
            return emptyResponse;
        }

        AssistantIntentDto intent = classifyIntent(userMessage, authentication);
        String resolvedIntent = normalizeIntent(intent, userMessage);

        switch (resolvedIntent) {
            case "PLACE_ORDER":
                return placeOrder(intent, userMessage, authentication);
            case "ADD_TO_CART":
                return addToCart(intent, userMessage, authentication);
            case "SEARCH_PRODUCTS":
                return searchProducts(intent, userMessage);
            case "LOOKUP_ORDERS":
                return lookupOrders(intent, userMessage, authentication);
            default:
                return generalHelp(userMessage, intent, authentication);
        }
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
                .add("LOOKUP_ORDERS")
                .add("GENERAL_HELP");
        properties.putObject("query").put("type", "string");
        properties.putObject("sku").put("type", "string");
        properties.putObject("category").put("type", "string");
        properties.putObject("quantity").put("type", "integer");
        properties.putObject("orderNumber").put("type", "string");

        schema.putArray("required")
                .add("intent")
                .add("query")
                .add("sku")
                .add("category")
                .add("quantity")
                .add("orderNumber");
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
                ? "The shopper is signed in and may ask you to browse products, add items to cart, place an order, or look up orders."
                : "The shopper is not signed in. Search and browse requests are allowed, but cart, checkout, and order lookup actions require sign-in.";
        return "You are a shopping assistant for an online store. " + userContext + " "
                + "Return only JSON that matches the schema. "
                + "Use intent SEARCH_PRODUCTS for product browsing, category browsing, deal browsing, or product lookup requests. "
                + "Use intent ADD_TO_CART for cart requests. "
                + "Use intent PLACE_ORDER for checkout requests, including messages like buy this, order this, or place my order. "
                + "Use intent LOOKUP_ORDERS for order history or order status requests. "
                + "Use intent GENERAL_HELP for everything else. "
                + "Use empty strings for unknown text fields. Use quantity 1 when not specified. "
                + "If the user asks for all products or general browsing, leave query blank unless a category is clearly named. "
                + "If the user mentions a product name, put the best search terms in query. "
                + "If the user mentions a SKU, put it in sku. "
                + "If the user mentions an order number, put it in orderNumber.";
    }

    private ShoppingAssistantResponseDto searchProducts(AssistantIntentDto intent, String fallbackQuery) {
        String query = normalizeProductQuery(firstNonBlank(intent.getQuery(), fallbackQuery));
        String category = normalizeCategory(intent.getCategory());
        List<ItemDto> matches;
        ShoppingAssistantResponseDto response = new ShoppingAssistantResponseDto();
        response.setIntent("SEARCH_PRODUCTS");
        response.setState("success");
        response.setResolvedQuery(query);

        if (isBrowseAllRequest(fallbackQuery, intent)) {
            matches = loadAllItems(6);
            response.setReply(matches.isEmpty()
                    ? "I couldn't load products right now."
                    : "Here are products from across the store.");
            response.getActions().add(action("Browse all products", "/index.html", "navigate"));
        } else if (!category.isBlank() && query.isBlank()) {
            matches = loadItemsByCategory(category, 4);
            response.setReply(matches.isEmpty()
                    ? "I couldn't find products in " + category + "."
                    : "Here are products in " + category + ".");
            response.getActions().add(action("Open " + category, "/index.html?category=" + urlEncode(category), "navigate"));
        } else {
            try {
                matches = searchItems(query, category, 4);
            } catch (Exception ex) {
                matches = new ArrayList<>();
            }
            response.setReply(matches.isEmpty()
                    ? "I could not find matching products for \"" + firstNonBlank(query, fallbackQuery) + "\". Try a different search term or browse a category."
                    : "I found " + matches.size() + " product" + (matches.size() == 1 ? "" : "s") + " for \"" + firstNonBlank(query, fallbackQuery) + "\".");
            if (!query.isBlank()) {
                response.getActions().add(action("Open search results", "/index.html?q=" + urlEncode(query), "navigate"));
            }
        }
        if (matches.isEmpty()) {
            response.setState("empty");
            response.getActions().add(action("Browse home", "/index.html", "navigate"));
            return response;
        }
        response.setItems(matches.stream().map(this::toAssistantItem).collect(Collectors.toList()));
        response.getActions().add(action("View cart", "/cart.html", "navigate"));
        return response;
    }

    private ShoppingAssistantResponseDto addToCart(AssistantIntentDto intent, String fallbackQuery, Authentication authentication) {
        ShoppingAssistantResponseDto response = new ShoppingAssistantResponseDto();
        response.setIntent("ADD_TO_CART");
        response.setState("success");
        if (!isAuthenticated(authentication)) {
            response.setRequiresSignIn(true);
            response.setState("needs_sign_in");
            response.setReply("Sign in first and I can add items to your cart.");
            response.getActions().add(action("Sign in", "/signin.html", "navigate"));
            return response;
        }

        String token = bearerToken(authentication);
        Long customerId = currentCustomerId(authentication);
        if (token == null || customerId == null) {
            response.setRequiresSignIn(true);
            response.setState("needs_sign_in");
            response.setReply("I need your signed-in session to add items to the cart.");
            return response;
        }

        List<ItemDto> candidates = resolveProductCandidates(intent, fallbackQuery, 3);
        if (candidates.isEmpty()) {
            response.setState("empty");
            response.setReply("I could not find that product to add it to your cart.");
            response.getActions().add(action("Browse products", "/index.html", "navigate"));
            return response;
        }
        if (candidates.size() > 1 && !hasSpecificSku(intent)) {
            response.setState("clarification");
            response.setReply("I found a few matches. Pick one and I can add it to your cart.");
            response.setItems(candidates.stream().map(this::toAssistantItem).collect(Collectors.toList()));
            return response;
        }

        ItemDto item = candidates.get(0);
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
        CartSnapshot cart = loadCart(customerId, token);

        response.setReply("I added " + quantity + " x " + item.getItemName() + " to your cart.");
        response.getItems().add(toAssistantItem(item));
        response.setCartItemCount(cart.items.stream().mapToInt(itemSnapshot -> itemSnapshot.getQuantity() == null ? 0 : itemSnapshot.getQuantity()).sum());
        response.getActions().add(action("Go to cart", "/cart.html", "navigate"));
        response.getActions().add(action("Checkout", "/checkout.html", "navigate"));
        return response;
    }

    private ShoppingAssistantResponseDto placeOrder(AssistantIntentDto intent, String fallbackQuery, Authentication authentication) {
        ShoppingAssistantResponseDto response = new ShoppingAssistantResponseDto();
        response.setIntent("PLACE_ORDER");
        response.setState("success");
        if (!isAuthenticated(authentication)) {
            response.setRequiresSignIn(true);
            response.setState("needs_sign_in");
            response.setReply("Sign in first so I can place an order for you.");
            response.getActions().add(action("Sign in", "/signin.html", "navigate"));
            return response;
        }

        String token = bearerToken(authentication);
        Long customerId = currentCustomerId(authentication);
        if (token == null || customerId == null) {
            response.setRequiresSignIn(true);
            response.setState("needs_sign_in");
            response.setReply("I need your signed-in session before I can place an order.");
            return response;
        }

        boolean directProductOrder = hasProductRequest(intent, fallbackQuery);
        ItemDto directOrderItem = null;
        if (directProductOrder) {
            List<ItemDto> candidates = resolveProductCandidates(intent, fallbackQuery, 3);
            if (candidates.isEmpty()) {
                response.setState("empty");
                response.setReply("I could not find the product you wanted to order.");
                response.getActions().add(action("Browse products", "/index.html", "navigate"));
                return response;
            }
            if (candidates.size() > 1 && !hasSpecificSku(intent)) {
                response.setState("clarification");
                response.setReply("I found a few matches. Pick one and I can add it, then place the order.");
                response.setItems(candidates.stream().map(this::toAssistantItem).collect(Collectors.toList()));
                return response;
            }
            directOrderItem = candidates.get(0);
            response.getItems().add(toAssistantItem(directOrderItem));
        }

        AccountDto account = accountService.getCurrentAccount(authentication.getName());
        CartSnapshot cart = directProductOrder
                ? singleItemCart(directOrderItem, resolveQuantity(intent))
                : loadCart(customerId, token);
        if (cart.items.isEmpty()) {
            response.setState("empty");
            response.setReply("Your cart is empty. Add a few items and I can place the order.");
            response.getActions().add(action("Browse products", "/index.html", "navigate"));
            return response;
        }

        AddressSnapshotDto shippingAddress = selectAddress(account);
        if (shippingAddress == null) {
            response.setState("needs_account_setup");
            response.setReply("I found items in your cart, but you need a saved shipping address before I can place the order.");
            response.getActions().add(action("Update account", "/account.html", "navigate"));
            return response;
        }

        StoredPaymentMethodDto paymentMethod = selectPaymentMethod(account);
        if (paymentMethod == null) {
            response.setState("needs_account_setup");
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
        if (!directProductOrder) {
            clearCart(customerId, token, cart.items);
        }

        OrderDto latestOrder = waitForLatestOrderState(order.getOrderNumber(), token);

        response.setOrderNumber(latestOrder.getOrderNumber());
        response.setOrderStatus(latestOrder.getStatus() == null ? "" : latestOrder.getStatus().name());
        response.setCheckoutUrl("/order-status.html?orderNumber=" + urlEncode(latestOrder.getOrderNumber()));
        response.setOrders(Collections.singletonList(toAssistantOrder(latestOrder)));
        if (latestOrder.getStatus() == OrderStatus.PAID) {
            response.setReply("Your order was placed and payment was captured.");
        } else if (latestOrder.getStatus() == OrderStatus.FAILED) {
            response.setState("error");
            response.setReply("The order was created, but payment failed.");
        } else {
            response.setState("processing");
            response.setReply("Your order was placed. Payment is processing now.");
        }
        response.getActions().add(action("View order", response.getCheckoutUrl(), "navigate"));
        response.getActions().add(action("Continue shopping", "/index.html", "navigate"));
        return response;
    }

    private ShoppingAssistantResponseDto lookupOrders(AssistantIntentDto intent, String userMessage, Authentication authentication) {
        ShoppingAssistantResponseDto response = new ShoppingAssistantResponseDto();
        response.setIntent("LOOKUP_ORDERS");
        if (!isAuthenticated(authentication)) {
            response.setRequiresSignIn(true);
            response.setState("needs_sign_in");
            response.setReply("Sign in first and I can look up your orders.");
            response.getActions().add(action("Sign in", "/signin.html", "navigate"));
            return response;
        }
        String token = bearerToken(authentication);
        Long customerId = currentCustomerId(authentication);
        if (token == null || customerId == null) {
            response.setRequiresSignIn(true);
            response.setState("needs_sign_in");
            response.setReply("I need your signed-in session before I can look up your orders.");
            return response;
        }

        List<OrderDto> orders = loadOrders(customerId, token);
        if (orders.isEmpty()) {
            response.setState("empty");
            response.setReply("I couldn't find any orders for your account yet.");
            response.getActions().add(action("Browse products", "/index.html", "navigate"));
            return response;
        }

        String requestedOrderNumber = firstNonBlank(intent.getOrderNumber(), extractOrderNumber(userMessage));
        List<OrderDto> matches = filterOrders(orders, requestedOrderNumber, userMessage);
        response.setState("success");
        if (matches.isEmpty()) {
            response.setState("empty");
            response.setReply(requestedOrderNumber.isBlank()
                    ? "I couldn't find a matching order."
                    : "I couldn't find order " + requestedOrderNumber + ".");
            response.getActions().add(action("View account", "/account.html", "navigate"));
            return response;
        }

        response.setOrders(matches.stream().map(this::toAssistantOrder).collect(Collectors.toList()));
        OrderDto firstOrder = matches.get(0);
        response.setOrderNumber(firstOrder.getOrderNumber());
        response.setOrderStatus(firstOrder.getStatus() == null ? "" : firstOrder.getStatus().name());
        response.setCheckoutUrl("/order-status.html?orderNumber=" + urlEncode(firstOrder.getOrderNumber()));
        response.setReply(matches.size() == 1
                ? "Here is the latest status for order " + firstOrder.getOrderNumber() + "."
                : "Here are the matching orders from your account.");
        response.getActions().add(action("View account", "/account.html", "navigate"));
        response.getActions().add(action("Open order", response.getCheckoutUrl(), "navigate"));
        return response;
    }

    private ShoppingAssistantResponseDto generalHelp(String userMessage, AssistantIntentDto intent, Authentication authentication) {
        ShoppingAssistantResponseDto response = new ShoppingAssistantResponseDto();
        response.setIntent("GENERAL_HELP");
        response.setState("idle");
        response.setReply(isAuthenticated(authentication)
                ? "I can browse the full catalog, search products by name or category, add items to your cart, place an order, and look up your orders."
                : "I can browse the full catalog and search products. Sign in if you want me to add to cart, place an order, or look up orders.");
        String query = normalizeProductQuery(firstNonBlank(intent.getQuery(), userMessage));
        List<ItemDto> matches = query.isBlank() ? loadAllItems(4) : searchItems(query, normalizeCategory(intent.getCategory()), 4);
        if (!matches.isEmpty()) {
            response.setItems(matches.stream().map(this::toAssistantItem).collect(Collectors.toList()));
            if (!query.isBlank()) {
                response.getActions().add(action("See results", "/index.html?q=" + urlEncode(query), "navigate"));
            }
        }
        response.getActions().add(action("Browse all products", "/index.html", "navigate"));
        if (isAuthenticated(authentication)) {
            response.getActions().add(action("View recent orders", "/account.html", "navigate"));
        }
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
            throw new IllegalStateException("Product search failed", ex);
        }
    }

    private List<ItemDto> loadAllItems(int limit) {
        try {
            JsonNode node = readJson(sendRequest(itemServiceUrl("/api/v1/shopping/items"), null, "GET"));
            List<ItemDto> items = objectMapper.readValue(node.toString(), new TypeReference<List<ItemDto>>() {});
            return items.stream().limit(limit).collect(Collectors.toList());
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }

    private List<ItemDto> loadItemsByCategory(String category, int limit) {
        if (category == null || category.isBlank()) {
            return new ArrayList<>();
        }
        try {
            String url = itemServiceUrl("/api/v1/shopping/items/category/" + urlEncode(category) + "?limit=" + limit);
            JsonNode node = readJson(sendRequest(url, null, "GET"));
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

    private List<OrderDto> loadOrders(Long customerId, String token) {
        try {
            JsonNode node = readJson(sendRequest(orderServiceUrl("/api/v1/shopping/orders/customers/" + customerId), token, "GET"));
            List<OrderDto> orders = objectMapper.readValue(node.toString(), new TypeReference<List<OrderDto>>() {});
            orders.sort(Comparator.comparing(OrderDto::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
            return orders;
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }

    private Optional<OrderDto> getOrder(String orderNumber, String token) {
        if (orderNumber == null || orderNumber.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode node = readJson(sendRequest(orderServiceUrl("/api/v1/shopping/orders/" + urlEncode(orderNumber)), token, "GET"));
            return Optional.of(objectMapper.treeToValue(node, OrderDto.class));
        } catch (Exception ex) {
            return Optional.empty();
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

    private String normalizeIntent(AssistantIntentDto intentDto, String userMessage) {
        String intent = intentDto == null ? null : intentDto.getIntent();
        if (intent == null || intent.isBlank()) {
            throw new IllegalStateException("OpenAI assistant classification returned no intent");
        }
        String normalized = intent.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_INTENTS.contains(normalized)) {
            throw new IllegalStateException("OpenAI assistant returned unsupported intent: " + intent);
        }
        String lower = userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT);
        if ("GENERAL_HELP".equals(normalized)) {
            if (containsAny(lower, "where is my order", "show my orders", "recent orders", "track order", "order status")) {
                return "LOOKUP_ORDERS";
            }
            if (containsAny(lower, "add to cart", "add this", "put this in my cart")) {
                return "ADD_TO_CART";
            }
            if (containsAny(lower, "buy ", "order this", "place my order", "checkout")) {
                return "PLACE_ORDER";
            }
            if (containsAny(lower, "find ", "search ", "show ", "browse ", "look for")) {
                return "SEARCH_PRODUCTS";
            }
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

    private int resolveQuantity(AssistantIntentDto intent) {
        return intent.getQuantity() == null || intent.getQuantity() < 1 ? 1 : intent.getQuantity();
    }

    private BigDecimal defaultMoney(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private List<ItemDto> resolveProductCandidates(AssistantIntentDto intent, String fallbackQuery, int limit) {
        if (intent == null) {
            return new ArrayList<>();
        }
        if (hasSpecificSku(intent)) {
            return getItemBySku(intent.getSku()).map(Collections::singletonList).orElseGet(ArrayList::new);
        }
        String query = normalizeProductQuery(firstNonBlank(intent.getQuery(), fallbackQuery));
        String category = normalizeCategory(intent.getCategory());
        if (!query.isBlank()) {
            try {
                List<ItemDto> matches = searchItems(query, category, limit);
                if (!matches.isEmpty()) {
                    return matches;
                }
            } catch (Exception ignored) {
                // Let the assistant fall back to category browsing or an empty result.
            }
        }
        if (!category.isBlank()) {
            return loadItemsByCategory(category, limit);
        }
        return new ArrayList<>();
    }

    private boolean hasSpecificSku(AssistantIntentDto intent) {
        return intent.getSku() != null && !intent.getSku().isBlank();
    }

    private boolean hasProductRequest(AssistantIntentDto intent, String fallbackQuery) {
        String normalizedQuery = normalizeProductQuery(firstNonBlank(intent.getQuery(), fallbackQuery));
        String normalizedCategory = normalizeCategory(intent.getCategory());
        boolean hasExplicitProduct = hasSpecificSku(intent)
                || !normalizedQuery.isBlank()
                || !normalizedCategory.isBlank();
        if (hasExplicitProduct) {
            return true;
        }
        String lower = firstNonBlank(fallbackQuery, "").toLowerCase(Locale.ROOT);
        return !containsAny(lower,
                "place my order",
                "place the order",
                "checkout my cart",
                "check out my cart",
                "order my cart");
    }

    private void addItemToCart(Long customerId, String token, ItemDto item, int quantity) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("itemId", item.getSku() + "::SHIPPING");
        payload.put("sku", item.getSku());
        payload.put("quantity", quantity);
        try {
            sendJson(orderServiceUrl("/api/v1/shopping/carts/" + customerId + "/items"), payload, token);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to add item to cart", ex);
        }
    }

    private OrderDto waitForLatestOrderState(String orderNumber, String token) {
        OrderDto latest = getOrder(orderNumber, token).orElseThrow(() -> new IllegalStateException("Failed to load the created order"));
        for (int attempt = 0; attempt < 4; attempt++) {
            if (latest.getStatus() != null && latest.getStatus() != OrderStatus.CREATED) {
                return latest;
            }
            try {
                Thread.sleep(700);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return latest;
            }
            latest = getOrder(orderNumber, token).orElse(latest);
        }
        return latest;
    }

    private ShoppingAssistantOrderDto toAssistantOrder(OrderDto order) {
        ShoppingAssistantOrderDto assistantOrder = new ShoppingAssistantOrderDto();
        assistantOrder.setOrderNumber(order.getOrderNumber());
        assistantOrder.setStatus(order.getStatus() == null ? "" : order.getStatus().name());
        assistantOrder.setStatusReason(order.getStatusReason());
        assistantOrder.setTotalAmount(order.getTotalAmount());
        assistantOrder.setCurrencyCode(firstNonBlank(order.getCurrencyCode(), "USD"));
        assistantOrder.setCreatedAt(order.getCreatedAt());
        assistantOrder.setUpdatedAt(order.getUpdatedAt());
        assistantOrder.setPaymentReference(order.getPaymentReference());
        assistantOrder.setOrderUrl("/order-status.html?orderNumber=" + urlEncode(order.getOrderNumber()));
        return assistantOrder;
    }

    private List<OrderDto> filterOrders(List<OrderDto> orders, String requestedOrderNumber, String userMessage) {
        if (orders == null || orders.isEmpty()) {
            return new ArrayList<>();
        }
        if (requestedOrderNumber != null && !requestedOrderNumber.isBlank()) {
            return orders.stream()
                    .filter(order -> requestedOrderNumber.equalsIgnoreCase(order.getOrderNumber()))
                    .limit(1)
                    .collect(Collectors.toList());
        }
        String lower = userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT);
        Integer dayWindow = extractDayWindow(userMessage);
        if (dayWindow != null) {
            Instant cutoff = Instant.now().minus(Duration.ofDays(dayWindow));
            return orders.stream()
                    .filter(order -> order.getCreatedAt() != null && !order.getCreatedAt().isBefore(cutoff))
                    .limit(10)
                    .collect(Collectors.toList());
        }
        if (containsAny(lower, "latest order", "last order", "most recent order")) {
            return orders.stream().limit(1).collect(Collectors.toList());
        }
        if (containsAny(lower, "cancelled")) {
            return orders.stream().filter(order -> order.getStatus() == OrderStatus.CANCELLED).limit(5).collect(Collectors.toList());
        }
        if (containsAny(lower, "refunded")) {
            return orders.stream().filter(order -> order.getStatus() == OrderStatus.REFUNDED).limit(5).collect(Collectors.toList());
        }
        if (containsAny(lower, "failed")) {
            return orders.stream().filter(order -> order.getStatus() == OrderStatus.FAILED).limit(5).collect(Collectors.toList());
        }
        return orders.stream().limit(3).collect(Collectors.toList());
    }

    private String extractOrderNumber(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        Matcher matcher = ORDER_NUMBER_PATTERN.matcher(text);
        return matcher.find() ? matcher.group(1).toUpperCase(Locale.ROOT) : "";
    }

    private boolean isBrowseAllRequest(String userMessage, AssistantIntentDto intent) {
        String lower = userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT);
        return firstNonBlank(intent.getQuery(), "").isBlank()
                && firstNonBlank(intent.getCategory(), "").isBlank()
                && containsAny(lower, "all products", "all items", "show products", "browse products", "look at products");
    }

    private String normalizeProductQuery(String query) {
        String normalized = firstNonBlank(query, "").trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "place my order", "show my orders", "where is my order")) {
            return "";
        }
        normalized = normalized.replaceAll("(?i),?\\s*place (?:my|the)?\\s*order(?: for me)?", "");
        normalized = normalized.replaceAll("(?i),?\\s*checkout(?: my cart)?", "");
        normalized = normalized.replaceAll("(?i)^i added\\s+", "");
        normalized = normalized.replaceAll("(?i)\\s+to\\s+the\\s+ca(?:r|rd)t.*$", "");
        normalized = normalized.replaceAll("(?i)\\s+to\\s+my\\s+cart.*$", "");
        normalized = normalized.replaceAll("(?i)^add\\s+", "");
        return normalized.trim();
    }

    private Integer extractDayWindow(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = DAY_WINDOW_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        try {
            int days = Integer.parseInt(matcher.group(1));
            return days > 0 ? days : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String normalizeCategory(String category) {
        return firstNonBlank(category, "").trim();
    }

    private boolean containsAny(String text, String... phrases) {
        for (String phrase : phrases) {
            if (text.contains(phrase)) {
                return true;
            }
        }
        return false;
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

    private CartSnapshot singleItemCart(ItemDto item, int quantity) {
        CartSnapshot snapshot = new CartSnapshot();
        if (item == null) {
            return snapshot;
        }
        snapshot.currencyCode = firstNonBlank(item.getCurrencyCode(), "USD");
        CartItemSnapshot cartItem = new CartItemSnapshot();
        cartItem.setItemId(item.getSku() + "::SHIPPING");
        cartItem.setSku(item.getSku());
        cartItem.setItemName(item.getItemName());
        cartItem.setUpc(item.getUpc());
        cartItem.setQuantity(quantity);
        cartItem.setUnitPrice(item.getUnitPrice());
        cartItem.setLineTotal(defaultMoney(item.getUnitPrice()).multiply(BigDecimal.valueOf(quantity)));
        snapshot.items.add(cartItem);
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
