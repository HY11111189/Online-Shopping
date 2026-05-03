package com.chuwa.shopping.assistant.service;

import com.chuwa.shopping.account.dto.AccountDto;
import com.chuwa.shopping.account.dto.AddressDto;
import com.chuwa.shopping.account.dto.StoredPaymentMethodDto;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
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

    /**
     * Shopping assistant pipeline:
     * 1. Accept a chat message or a clicked product/action card.
     * 2. Ask OpenAI for a structured classification JSON.
     * 3. Turn that JSON into the real item/order/account service call.
     * 4. Wait for the service result.
     * 5. Build a response JSON for the frontend to render.
     *
     * The model only classifies. The service owns the business flow.
     */

    private static final Logger log = LoggerFactory.getLogger(ShoppingAssistantService.class);
    private static final String DEFAULT_OPENAI_MODEL = "gpt-5.1";

    private static final List<String> SUPPORTED_INTENTS = List.of(
            "SEARCH_PRODUCTS",
            "PLACE_ORDER",
            "LOOKUP_ORDERS",
            "GENERAL_HELP"
    );
    private static final Pattern ORDER_NUMBER_PATTERN = Pattern.compile("(ORD-[A-Z0-9]{8,})", Pattern.CASE_INSENSITIVE);

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
        // Main entry point from the chat widget.
        // A clicked product/action card is handled first because it already contains
        // a structured SKU and action, so we do not need to re-classify the text.
        if (hasDirectSelection(requestDto)) {
            return handleDirectSelection(requestDto, authentication);
        }
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
                return placeOrder(intent, authentication, false);
            case "SEARCH_PRODUCTS":
                return searchProducts(intent, userMessage);
            case "LOOKUP_ORDERS":
                return lookupOrders(intent, userMessage, authentication);
            default:
                return generalHelp(userMessage, intent, authentication);
        }
    }

    private boolean hasDirectSelection(ShoppingAssistantRequestDto requestDto) {
        if (requestDto == null) {
            return false;
        }
        String action = requestDto.getSelectedAction();
        String sku = requestDto.getSelectedSku();
        return action != null && !action.isBlank() && sku != null && !sku.isBlank();
    }

    private ShoppingAssistantResponseDto handleDirectSelection(ShoppingAssistantRequestDto requestDto, Authentication authentication) {
        // Direct-selection requests come from the product cards.
        // They already tell us which SKU the user picked and which action to take.
        // That lets the backend skip OpenAI and jump straight into the chosen flow.
        AssistantIntentDto intent = new AssistantIntentDto();
        intent.setSku(requestDto.getSelectedSku());
        intent.setProductName(firstNonBlank(requestDto.getSelectedItemName(), requestDto.getSelectedSku()));
        intent.setSearchQuery(requestDto.getSelectedSku());
        intent.setQuantity(1);
        intent.setBrowseAll(false);
        String action = requestDto.getSelectedAction().trim().toUpperCase(Locale.ROOT);
        if ("ADD_TO_CART".equals(action)) {
            throw new IllegalStateException("Unsupported assistant action: " + requestDto.getSelectedAction());
        }
        if ("PLACE_ORDER".equals(action)) {
            intent.setIntent("PLACE_ORDER");
            return placeOrder(intent, authentication, true);
        }
        throw new IllegalStateException("Unsupported assistant action: " + requestDto.getSelectedAction());
    }

    private AssistantIntentDto classifyIntent(String userMessage, Authentication authentication) {
        // Ask OpenAI for classification JSON only.
        // The returned JSON is the assistant's internal routing hint, not a service request.
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
                .add("PLACE_ORDER")
                .add("LOOKUP_ORDERS")
                .add("GENERAL_HELP");
        properties.putObject("productName").put("type", "string");
        properties.putObject("searchQuery").put("type", "string");
        properties.putObject("sku").put("type", "string");
        properties.putObject("category").put("type", "string");
        properties.putObject("quantity").put("type", "integer");
        properties.putObject("orderNumber").put("type", "string");
        properties.putObject("startDate").put("type", "string");
        properties.putObject("endDate").put("type", "string");
        properties.putObject("browseAll").put("type", "boolean");

        schema.putArray("required")
                .add("intent")
                .add("productName")
                .add("searchQuery")
                .add("sku")
                .add("category")
                .add("quantity")
                .add("orderNumber")
                .add("startDate")
                .add("endDate")
                .add("browseAll");
        schema.put("additionalProperties", false);

        try {
            JsonNode response = postJsonToJson("https://api.openai.com/v1/responses", payload, openAiApiKey);
            String textOutput = extractOutputText(response);
            if (textOutput == null || textOutput.isBlank()) {
                throw new IllegalStateException("OpenAI assistant classification returned an empty response");
            }
            log.info("assistant classification raw JSON: {}", textOutput);
            AssistantIntentDto intent = objectMapper.readValue(textOutput, AssistantIntentDto.class);
            if (intent.getIntent() == null || intent.getIntent().isBlank()) {
                throw new IllegalStateException("OpenAI assistant classification returned no intent");
            }
            log.info("assistant classification parsed intent: {}", objectMapper.writeValueAsString(intent));
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
        // Prompt rules:
        // - classify into the supported intents
        // - extract only simple fields like product, sku, order number, and date range
        // - never invent API paths or backend behavior
        String today = LocalDate.now(ZoneId.systemDefault()).toString();
        String monthStart = LocalDate.now(ZoneId.systemDefault()).withDayOfMonth(1).toString();
        String nextMonthStart = LocalDate.now(ZoneId.systemDefault()).withDayOfMonth(1).plusMonths(1).toString();
        String weekStart = LocalDate.now(ZoneId.systemDefault()).with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).toString();
        String nextWeekStart = LocalDate.now(ZoneId.systemDefault()).with(TemporalAdjusters.next(java.time.DayOfWeek.MONDAY)).toString();
        String userContext = isAuthenticated(authentication)
                ? "The shopper is signed in and may ask you to browse products, add items to cart, place an order, or look up orders."
                : "The shopper is not signed in. Search and browse requests are allowed, but cart, checkout, and order lookup actions require sign-in.";
        return "You are a shopping assistant for an online store. " + userContext + " "
                + "Today is " + today + " in the shopper's local timezone. "
                + "Return only JSON that matches the schema. The backend will parse this JSON and map it into internal request objects. "
                + "Use only these intents: SEARCH_PRODUCTS, PLACE_ORDER, LOOKUP_ORDERS, and GENERAL_HELP. "
                + "Use SEARCH_PRODUCTS only when the shopper is asking to find, browse, show, or look at products and has not clearly asked to buy or add to cart. "
                + "Use PLACE_ORDER only when the shopper clearly wants to buy, order, checkout, or purchase an item. "
                + "If the message contains buy, order, purchase, checkout, or place my order, classify it as PLACE_ORDER, not SEARCH_PRODUCTS. "
                + "Use LOOKUP_ORDERS only when the shopper asks about order history, order status, or orders placed on a date or within a date range. "
                + "Use GENERAL_HELP only when none of the above intents fit. "
                + "Use empty strings for unknown text fields. Use quantity 1 when not specified. "
                + "Set productName to the product words only, not the whole user sentence. "
                + "Set searchQuery to short product search terms only. "
                + "If the user asks for all products or general browsing, set browseAll to true and leave productName and searchQuery blank unless a category is clearly named. "
                + "If the user mentions a product category, fill category with the category words only. "
                + "If the user mentions a SKU, put it in sku. "
                + "If the user mentions an order number, put it in orderNumber. "
                + "For LOOKUP_ORDERS, always return startDate and endDate as ISO-8601 date strings. "
                + "The backend will use startDate inclusive and endDate exclusive, so endDate should be the day after the last day to include. "
                + "The examples below show how to encode today, yesterday, this week, this month, last 7 days, and a specific day. "
                + "Example JSON for 'buy me some medicine': "
                + "{\"intent\":\"PLACE_ORDER\",\"productName\":\"medicine\",\"searchQuery\":\"medicine\",\"sku\":\"\",\"category\":\"\",\"quantity\":1,\"orderNumber\":\"\",\"startDate\":\"\",\"endDate\":\"\",\"browseAll\":false}. "
                + "Example JSON for 'find me some fruits': "
                + "{\"intent\":\"SEARCH_PRODUCTS\",\"productName\":\"fruits\",\"searchQuery\":\"fruits\",\"sku\":\"\",\"category\":\"\",\"quantity\":1,\"orderNumber\":\"\",\"startDate\":\"\",\"endDate\":\"\",\"browseAll\":false}. "
                + "Example JSON for 'show me orders I placed this week': "
                + "{\"intent\":\"LOOKUP_ORDERS\",\"productName\":\"\",\"searchQuery\":\"\",\"sku\":\"\",\"category\":\"\",\"quantity\":1,\"orderNumber\":\"\",\"startDate\":\"2026-04-27\",\"endDate\":\"2026-05-04\",\"browseAll\":false}. "
                + "Example JSON for 'show me orders I placed this month': "
                + "{\"intent\":\"LOOKUP_ORDERS\",\"productName\":\"\",\"searchQuery\":\"\",\"sku\":\"\",\"category\":\"\",\"quantity\":1,\"orderNumber\":\"\",\"startDate\":\"" + monthStart + "\",\"endDate\":\"" + nextMonthStart + "\",\"browseAll\":false}. "
                + "Example JSON for 'show me orders I placed this week': "
                + "{\"intent\":\"LOOKUP_ORDERS\",\"productName\":\"\",\"searchQuery\":\"\",\"sku\":\"\",\"category\":\"\",\"quantity\":1,\"orderNumber\":\"\",\"startDate\":\"" + weekStart + "\",\"endDate\":\"" + nextWeekStart + "\",\"browseAll\":false}. "
                + "Example JSON for 'show me orders I placed yesterday': "
                + "{\"intent\":\"LOOKUP_ORDERS\",\"productName\":\"\",\"searchQuery\":\"\",\"sku\":\"\",\"category\":\"\",\"quantity\":1,\"orderNumber\":\"\",\"startDate\":\"2026-04-30\",\"endDate\":\"2026-05-01\",\"browseAll\":false}. "
                + "Example JSON for 'orders I placed on April 30': "
                + "{\"intent\":\"LOOKUP_ORDERS\",\"productName\":\"\",\"searchQuery\":\"\",\"sku\":\"\",\"category\":\"\",\"quantity\":1,\"orderNumber\":\"\",\"startDate\":\"2026-04-30\",\"endDate\":\"2026-05-01\",\"browseAll\":false}.";
    }

    private ShoppingAssistantResponseDto searchProducts(AssistantIntentDto intent, String fallbackQuery) {
        // Search flow:
        // 1) use the AI fields to decide whether this is a text search, category browse, or browse-all request
        // 2) call item-service to get matching products
        // 3) return product cards so the user can optionally continue with cart or order actions
        String query = assistantSearchQuery(intent);
        String category = normalizeCategory(intent.getCategory());
        List<ItemDto> matches;
        ShoppingAssistantResponseDto response = new ShoppingAssistantResponseDto();
        response.setIntent("SEARCH_PRODUCTS");
        response.setState("choose_product");
        response.setResolvedQuery(query);

        if (isBrowseAllRequest(intent)) {
            matches = loadAllItems(5);
            response.setReply(matches.isEmpty()
                    ? "I couldn't load products right now."
                    : "Here are products from across the store. Pick one and I can add it to your cart or place the order.");
            response.getActions().add(action("Browse all products", "/index.html", "navigate"));
        } else if (!category.isBlank() && query.isBlank()) {
            matches = loadItemsByCategory(category, 4);
            response.setReply(matches.isEmpty()
                    ? "I couldn't find products in " + category + "."
                    : "Here are products in " + category + ". Pick one and tell me whether you want it in your cart or ordered now.");
            response.getActions().add(action("Open " + category, "/index.html?category=" + urlEncode(category), "navigate"));
        } else {
            try {
                matches = searchItems(query, category, 4);
            } catch (Exception ex) {
                matches = new ArrayList<>();
            }
            response.setReply(matches.isEmpty()
                    ? "I could not find matching products for \"" + displaySearchLabel(intent, query, category) + "\". Try a different search term or browse a category."
                    : "I found " + matches.size() + " product" + (matches.size() == 1 ? "" : "s") + " for \"" + displaySearchLabel(intent, query, category) + "\". Pick one and I can add it to your cart or place the order.");
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

    private ShoppingAssistantResponseDto addToCart(AssistantIntentDto intent, Authentication authentication) {
        // Cart flow:
        // resolve one product, push it to order-service, then return a cart confirmation.
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

        List<ItemDto> candidates = resolveProductCandidates(intent, 3);
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

    private ShoppingAssistantResponseDto placeOrder(AssistantIntentDto intent, Authentication authentication, boolean confirmedSelection) {
        // Order flow:
        // 1) resolve the selected product
        // 2) load the signed-in account details needed for checkout
        // 3) create a draft order in order-service
        // 4) place the draft order and return the checkout result
        ShoppingAssistantResponseDto response = new ShoppingAssistantResponseDto();
        response.setIntent("PLACE_ORDER");
        response.setState("choose_product");
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

        boolean productRequest = hasProductRequest(intent) || hasSpecificSku(intent);
        List<ItemDto> candidates = resolveProductCandidates(intent, 4);

        if (candidates.isEmpty()) {
            if (!productRequest) {
                List<ItemDto> browseMatches = loadAllItems(5);
                if (browseMatches.isEmpty()) {
                    response.setState("empty");
                    response.setReply("I couldn't load products right now. Tell me what you'd like to buy.");
                    response.getActions().add(action("Browse products", "/index.html", "navigate"));
                    return response;
                }
                response.setReply("Pick a product and I can buy it for you.");
                response.setItems(browseMatches.stream().map(this::toAssistantItem).collect(Collectors.toList()));
                response.getActions().add(action("Browse all products", "/index.html", "navigate"));
                return response;
            }
            response.setState("empty");
            response.setReply("I could not find the product you wanted to buy.");
            response.getActions().add(action("Browse products", "/index.html", "navigate"));
            return response;
        }

        if (!confirmedSelection) {
            response.setReply(candidates.size() == 1
                    ? "I found one match. Click Buy this to place it now."
                    : "I found a few matches. Pick one and click Buy this to place it now.");
            response.setItems(candidates.stream().map(this::toAssistantItem).collect(Collectors.toList()));
            response.getActions().add(action("Browse products", "/index.html", "navigate"));
            return response;
        }

        ItemDto directOrderItem = candidates.get(0);
        response.getItems().add(toAssistantItem(directOrderItem));

        AccountDto account = accountService.getCurrentAccount(authentication.getName());
        CartSnapshot cart = singleItemCart(directOrderItem, resolveQuantity(intent));
        AddressSnapshotDto shippingAddress = selectAddress(account);
        if (shippingAddress == null) {
            response.setState("needs_account_setup");
            response.setReply("You need a saved shipping address before I can place the order.");
            response.getActions().add(action("Update account", "/account.html", "navigate"));
            return response;
        }

        StoredPaymentMethodDto paymentMethod = selectPaymentMethod(account);
        if (paymentMethod == null) {
            response.setState("needs_account_setup");
            response.setReply("You need a saved payment method before I can place the order.");
            response.getActions().add(action("Update account", "/account.html", "navigate"));
            return response;
        }

        ObjectNode request = objectMapper.createObjectNode();
        request.put("customerId", customerId);
        request.put("currencyCode", firstNonBlank(cart.currencyCode, "USD"));
        request.put("taxAmount", "0.00");
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

        OrderDto draftOrder;
        try {
            draftOrder = readValue(sendJson(orderServiceUrl("/api/v1/shopping/orders"), request, token), OrderDto.class);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create draft order", ex);
        }
        OrderDto latestOrder;
        try {
            latestOrder = readValue(sendRequest(orderServiceUrl("/api/v1/shopping/orders/" + urlEncode(draftOrder.getOrderNumber()) + "/place"), token, "POST"), OrderDto.class);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to place order", ex);
        }

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
        // Order lookup flow:
        // 1) require sign-in
        // 2) load the customer's orders from order-service
        // 3) filter by exact order number or the AI-provided date range
        // 4) return matching order cards for the frontend
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
        // The AI gives us a normalized date window.
        // The backend applies that window to order.createdAt and then returns the matches.
        List<OrderDto> matches = filterOrders(orders, intent, requestedOrderNumber, userMessage);
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
        if (matches.size() == 1) {
            OrderDto firstOrder = matches.get(0);
            response.setOrderNumber(firstOrder.getOrderNumber());
            response.setOrderStatus(firstOrder.getStatus() == null ? "" : firstOrder.getStatus().name());
            response.setCheckoutUrl("/order-status.html?orderNumber=" + urlEncode(firstOrder.getOrderNumber()));
            response.setReply("Here is the latest status for order " + firstOrder.getOrderNumber() + ".");
        } else {
            response.setReply("Here are the matching orders from your account.");
            if (matches.size() >= 10) {
                response.getActions().add(action("Show more", "/account.html", "navigate"));
            }
        }
        response.getActions().add(action("View account", "/account.html", "navigate"));
        return response;
    }

    private ShoppingAssistantResponseDto generalHelp(String userMessage, AssistantIntentDto intent, Authentication authentication) {
        // General help is the fallback when the message does not fit the product, order, or lookup flows.
        ShoppingAssistantResponseDto response = new ShoppingAssistantResponseDto();
        response.setIntent("GENERAL_HELP");
        response.setState("idle");
        response.setReply(isAuthenticated(authentication)
                ? "I can browse the full catalog, search products by name or category, add items to your cart, place an order, and look up your orders."
                : "I can browse the full catalog and search products. Sign in if you want me to add to cart, place an order, or look up orders.");
        String query = assistantSearchQuery(intent);
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
        // item-service owns product data and search behavior.
        // The assistant only asks for matching items and then renders the results.
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
        // Used for browse-all requests and general help suggestions.
        try {
            JsonNode node = readJson(sendRequest(itemServiceUrl("/api/v1/shopping/items"), null, "GET"));
            List<ItemDto> items = objectMapper.readValue(node.toString(), new TypeReference<List<ItemDto>>() {});
            return items.stream().limit(limit).collect(Collectors.toList());
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }

    private List<ItemDto> loadItemsByCategory(String category, int limit) {
        // Used when the AI identifies a category browse, like "toys" or "health".
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
        // order-service returns the customer's orders; the assistant filters them locally.
        try {
            JsonNode node = readJson(sendRequest(orderServiceUrl("/api/v1/shopping/orders/customers/" + customerId), token, "GET"));
            List<OrderDto> orders = objectMapper.readValue(node.toString(), new TypeReference<List<OrderDto>>() {});
            orders.sort(Comparator.comparing(OrderDto::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
            return orders;
        } catch (Exception ex) {
            return new ArrayList<>();
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

    private ShoppingAssistantItemDto toAssistantItem(ItemDto item) {
        // Convert the catalog item into the smaller card shape that the frontend renders.
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
        // Small helper for consistent assistant action buttons.
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
        // Validate that the model returned one of the supported intents.
        String intent = intentDto == null ? null : intentDto.getIntent();
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

    private int resolveQuantity(AssistantIntentDto intent) {
        return intent.getQuantity() == null || intent.getQuantity() < 1 ? 1 : intent.getQuantity();
    }

    private BigDecimal defaultMoney(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private List<ItemDto> resolveProductCandidates(AssistantIntentDto intent, int limit) {
        // Resolve a concrete product candidate list from SKU, query, or category.
        // This is used by both cart and order flows after the AI classification step.
        if (intent == null) {
            return new ArrayList<>();
        }
        if (hasSpecificSku(intent)) {
            return getItemBySku(intent.getSku()).map(Collections::singletonList).orElseGet(ArrayList::new);
        }
        String query = assistantSearchQuery(intent);
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

    private boolean hasProductRequest(AssistantIntentDto intent) {
        String normalizedQuery = assistantSearchQuery(intent);
        String normalizedCategory = normalizeCategory(intent.getCategory());
        return hasSpecificSku(intent)
                || !normalizedQuery.isBlank()
                || !normalizedCategory.isBlank();
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

    private List<OrderDto> filterOrders(List<OrderDto> orders, AssistantIntentDto intent, String requestedOrderNumber, String userMessage) {
        if (orders == null || orders.isEmpty()) {
            return new ArrayList<>();
        }
        if (requestedOrderNumber != null && !requestedOrderNumber.isBlank()) {
            return orders.stream()
                    .filter(order -> requestedOrderNumber.equalsIgnoreCase(order.getOrderNumber()))
                    .limit(1)
                    .collect(Collectors.toList());
        }
        DateRange dateRange = extractDateRange(intent);
        if (dateRange != null) {
            // A specific day becomes a one-day range. "This week" and "this month"
            // are also just ranges, so the assistant does not need separate branches.
            return orders.stream()
                    .filter(order -> order.getCreatedAt() != null
                            && !order.getCreatedAt().isBefore(dateRange.start)
                            && order.getCreatedAt().isBefore(dateRange.end))
                    .collect(Collectors.toList());
        }
        String lower = userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT);
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

    private boolean isBrowseAllRequest(AssistantIntentDto intent) {
        return Boolean.TRUE.equals(intent.getBrowseAll())
                || (assistantSearchQuery(intent).isBlank()
                && firstNonBlank(intent.getCategory(), "").isBlank()
                && false);
    }

    private String assistantSearchQuery(AssistantIntentDto intent) {
        if (intent == null) {
            return "";
        }
        String searchQuery = firstNonBlank(intent.getSearchQuery(), "");
        if (!searchQuery.isBlank()) {
            return searchQuery.trim();
        }
        String productName = firstNonBlank(intent.getProductName(), "");
        if (!productName.isBlank()) {
            return productName.trim();
        }
        String category = firstNonBlank(intent.getCategory(), "");
        if (!category.isBlank()) {
            return category.trim();
        }
        return "";
    }

    private String displaySearchLabel(AssistantIntentDto intent, String query, String category) {
        if (!query.isBlank()) {
            return query;
        }
        if (!category.isBlank()) {
            return category;
        }
        String label = firstNonBlank(intent == null ? "" : intent.getProductName(), "");
        return label.isBlank() ? "products" : label;
    }

    private DateRange extractDateRange(AssistantIntentDto intent) {
        if (intent == null) {
            return null;
        }
        String startText = firstNonBlank(intent.getStartDate(), "");
        String endText = firstNonBlank(intent.getEndDate(), "");
        if (startText.isBlank() && endText.isBlank()) {
            return null;
        }
        LocalDate startDate = parseDate(startText);
        LocalDate endDate = parseDate(endText);
        if (startDate == null && endDate == null) {
            return null;
        }
        if (startDate == null) {
            startDate = endDate.minusDays(1);
        }
        if (endDate == null) {
            endDate = startDate.plusDays(1);
        }
        if (!endDate.isAfter(startDate)) {
            endDate = startDate.plusDays(1);
        }
        ZoneId zoneId = ZoneId.systemDefault();
        // startDate is inclusive; endDate is exclusive.
        // Example: 2026-04-30 to 2026-05-01 means "orders placed on April 30".
        return new DateRange(startDate.atStartOfDay(zoneId).toInstant(), endDate.atStartOfDay(zoneId).toInstant());
    }

    private LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (Exception ex) {
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

    private static class DateRange {
        private final Instant start;
        private final Instant end;

        private DateRange(Instant start, Instant end) {
            this.start = start;
            this.end = end;
        }
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
