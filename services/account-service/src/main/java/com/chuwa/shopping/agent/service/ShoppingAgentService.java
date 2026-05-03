package com.chuwa.shopping.agent.service;

import com.chuwa.shopping.account.dto.AccountDto;
import com.chuwa.shopping.account.dto.AddressDto;
import com.chuwa.shopping.account.dto.StoredPaymentMethodDto;
import com.chuwa.shopping.account.service.AccountService;
import com.chuwa.shopping.agent.dto.ShoppingAgentPlanDto;
import com.chuwa.shopping.agent.dto.ShoppingAgentToolCallDto;
import com.chuwa.shopping.assistant.dto.ShoppingAssistantActionDto;
import com.chuwa.shopping.assistant.dto.ShoppingAssistantItemDto;
import com.chuwa.shopping.assistant.dto.ShoppingAssistantOrderDto;
import com.chuwa.shopping.assistant.dto.ShoppingAssistantRequestDto;
import com.chuwa.shopping.assistant.dto.ShoppingAssistantResponseDto;
import com.chuwa.shopping.client.ItemServiceClient;
import com.chuwa.shopping.dto.item.ItemDto;
import com.chuwa.shopping.dto.order.AddressSnapshotDto;
import com.chuwa.shopping.dto.order.OrderDto;
import com.chuwa.shopping.dto.order.OrderLineItemDto;
import com.chuwa.shopping.dto.order.OrderStatus;
import com.chuwa.shopping.dto.payment.PaymentMethod;
import com.chuwa.shopping.dto.payment.PaymentStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

@Service
public class ShoppingAgentService {

    private static final Logger log = LoggerFactory.getLogger(ShoppingAgentService.class);
    private static final String DEFAULT_OPENAI_MODEL = "gpt-5.1";
    private static final Pattern ORDER_NUMBER_PATTERN = Pattern.compile("(ORD-[A-Z0-9]{8,})", Pattern.CASE_INSENSITIVE);

    private final AccountService accountService;
    private final ItemServiceClient itemServiceClient;
    private final ShoppingAgentMemoryService memoryService;
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

    public ShoppingAgentService(AccountService accountService,
                                ItemServiceClient itemServiceClient,
                                ShoppingAgentMemoryService memoryService,
                                ObjectMapper objectMapper) {
        this.accountService = accountService;
        this.itemServiceClient = itemServiceClient;
        this.memoryService = memoryService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public ShoppingAssistantResponseDto chat(ShoppingAssistantRequestDto requestDto, Authentication authentication) {
        if (hasDirectSelection(requestDto)) {
            return handleDirectSelection(requestDto, authentication);
        }

        String userMessage = requestDto == null || requestDto.getMessage() == null ? "" : requestDto.getMessage().trim();
        ShoppingAssistantResponseDto emptyResponse = new ShoppingAssistantResponseDto();
        if (userMessage.isBlank()) {
            emptyResponse.setIntent("GENERAL_HELP");
            emptyResponse.setState("idle");
            emptyResponse.setReply("Ask me to browse products, find an item, place an order, add something to your cart, or look up your orders.");
            emptyResponse.getActions().add(action("Browse all products", "/index.html", "navigate"));
            return emptyResponse;
        }

        String conversationKey = conversationKey(authentication);
        String normalizedMessage = ShoppingAgentMemoryService.normalize(userMessage);
        Optional<ShoppingAssistantResponseDto> cached = memoryService.findCachedResponse(conversationKey, normalizedMessage);
        if (cached.isPresent()) {
            log.info("agent cache hit for {}", conversationKey);
            return cached.get();
        }

        ShoppingAgentPlanDto plan = classifyPlan(userMessage, authentication, conversationKey);
        ShoppingAssistantResponseDto response = executePlan(plan, userMessage, authentication);
        memoryService.remember(conversationKey, userMessage, response);
        return response;
    }

    private boolean hasDirectSelection(ShoppingAssistantRequestDto requestDto) {
        if (requestDto == null) {
            return false;
        }
        return requestDto.getSelectedAction() != null
                && !requestDto.getSelectedAction().isBlank()
                && requestDto.getSelectedSku() != null
                && !requestDto.getSelectedSku().isBlank();
    }

    private ShoppingAssistantResponseDto handleDirectSelection(ShoppingAssistantRequestDto requestDto, Authentication authentication) {
        String action = requestDto.getSelectedAction().trim().toUpperCase(Locale.ROOT);
        ItemDto item = getItemBySku(requestDto.getSelectedSku());
        if (item == null) {
            ShoppingAssistantResponseDto response = new ShoppingAssistantResponseDto();
            response.setIntent("GENERAL_HELP");
            response.setState("empty");
            response.setReply("I could not load that product.");
            return response;
        }
        if ("ADD_TO_CART".equals(action)) {
            return addToCartDirect(item, authentication);
        }
        if ("PLACE_ORDER".equals(action)) {
            return placeOrderDirect(item, resolveQuantityFromSelection(requestDto), authentication);
        }
        throw new IllegalStateException("Unsupported agent action: " + requestDto.getSelectedAction());
    }

    private ShoppingAgentPlanDto classifyPlan(String userMessage, Authentication authentication, String conversationKey) {
        if (openAiApiKey == null || openAiApiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY is not configured for the shopping agent");
        }
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", resolveOpenAiModel());
        payload.put("max_output_tokens", 220);
        payload.put("input", userMessage);
        payload.put("instructions", buildAgentInstructions(authentication, memoryService.recentSummary(conversationKey)));

        ObjectNode text = payload.putObject("text");
        ObjectNode format = text.putObject("format");
        format.put("type", "json_schema");
        format.put("name", "shopping_agent_plan");
        format.put("strict", true);

        ObjectNode schema = format.putObject("schema");
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("intent").put("type", "string").putArray("enum")
                .add("SEARCH_PRODUCTS")
                .add("PLACE_ORDER")
                .add("LOOKUP_ORDERS")
                .add("ADD_TO_CART")
                .add("GENERAL_HELP");
        properties.putObject("reply").put("type", "string");
        properties.putObject("clarificationQuestion").put("type", "string");
        properties.putObject("needsClarification").put("type", "boolean");

        ObjectNode toolCalls = properties.putObject("toolCalls");
        toolCalls.put("type", "array");
        ObjectNode toolCallSchema = toolCalls.putObject("items");
        toolCallSchema.put("type", "object");
        ObjectNode toolProps = toolCallSchema.putObject("properties");
        toolProps.putObject("tool").put("type", "string").putArray("enum")
                .add("SEARCH_PRODUCTS")
                .add("LOOKUP_ORDERS")
                .add("ADD_TO_CART")
                .add("PLACE_ORDER");
        toolProps.putObject("query").put("type", "string");
        toolProps.putObject("category").put("type", "string");
        toolProps.putObject("sku").put("type", "string");
        toolProps.putObject("orderNumber").put("type", "string");
        toolProps.putObject("startDate").put("type", "string");
        toolProps.putObject("endDate").put("type", "string");
        toolProps.putObject("quantity").put("type", "integer");
        toolProps.putObject("browseAll").put("type", "boolean");
        toolProps.putObject("limit").put("type", "integer");
        toolCallSchema.putArray("required")
                .add("tool")
                .add("query")
                .add("category")
                .add("sku")
                .add("orderNumber")
                .add("startDate")
                .add("endDate")
                .add("quantity")
                .add("browseAll")
                .add("limit");
        toolCallSchema.put("additionalProperties", false);

        schema.putArray("required")
                .add("intent")
                .add("reply")
                .add("clarificationQuestion")
                .add("needsClarification")
                .add("toolCalls");
        schema.put("additionalProperties", false);

        try {
            JsonNode response = postJsonToJson("https://api.openai.com/v1/responses", payload, openAiApiKey);
            String textOutput = extractOutputText(response);
            if (textOutput == null || textOutput.isBlank()) {
                throw new IllegalStateException("OpenAI agent planning returned an empty response");
            }
            log.info("agent plan raw JSON: {}", textOutput);
            return objectMapper.readValue(textOutput, ShoppingAgentPlanDto.class);
        } catch (Exception ex) {
            throw new IllegalStateException("OpenAI agent planning failed: " + rootCauseMessage(ex), ex);
        }
    }

    private String buildAgentInstructions(Authentication authentication, String recentSummary) {
        String today = LocalDate.now(ZoneId.systemDefault()).toString();
        String userContext = isAuthenticated(authentication)
                ? "The shopper is signed in and the agent may add items to cart, place orders, and look up orders."
                : "The shopper is not signed in. The agent may browse and search products, but cart and checkout actions require sign-in.";
        return "You are an autonomous shopping agent. " + userContext + " "
                + "Today is " + today + " in the shopper's local timezone. "
                + "Return only JSON. The backend will execute the tool calls you plan. "
                + "Use the recent memory summary to avoid repeating API calls when the user asks the same thing again or refers to the last product. "
                + "Recent memory summary: " + (recentSummary == null || recentSummary.isBlank() ? "none" : recentSummary) + " "
                + "Use SEARCH_PRODUCTS for finding or browsing products. "
                + "Use PLACE_ORDER only when you can move directly toward checkout. "
                + "Use ADD_TO_CART when the user wants to save a product to cart. "
                + "Use LOOKUP_ORDERS for order history or date-based order lookups. "
                + "If the request is ambiguous, return needsClarification=true and set clarificationQuestion. "
                + "Do not ask follow-up questions for broad product requests like 'find some fruits' or 'any fruits on this website'; use a broad search and let the app show a few matches. "
                + "If the request is a direct buy and the product is obvious, include SEARCH_PRODUCTS first, then PLACE_ORDER. "
                + "If the request is a cart request, include SEARCH_PRODUCTS first, then ADD_TO_CART. "
                + "For order lookups, set startDate and endDate as ISO-8601 dates with endDate exclusive. "
                + "Examples: 'buy me some medicine' -> SEARCH_PRODUCTS then PLACE_ORDER. "
                + "'find me some fruits' -> SEARCH_PRODUCTS with a broad fruit search. "
                + "'show me orders I placed this week' -> LOOKUP_ORDERS with a full date range.";
    }

    private ShoppingAssistantResponseDto executePlan(ShoppingAgentPlanDto plan, String userMessage, Authentication authentication) {
        ShoppingAssistantResponseDto response = new ShoppingAssistantResponseDto();
        response.setIntent(firstNonBlank(plan == null ? null : plan.getIntent(), "GENERAL_HELP"));
        if (plan == null) {
            response.setState("error");
            response.setReply("I could not plan that request.");
            return response;
        }

        if (!"SEARCH_PRODUCTS".equals(firstNonBlank(plan.getIntent(), "GENERAL_HELP"))
                && plan.isNeedsClarification()
                && plan.getClarificationQuestion() != null
                && !plan.getClarificationQuestion().isBlank()) {
            response.setState("clarification");
            response.setReply(plan.getClarificationQuestion());
            return response;
        }

        String intent = firstNonBlank(plan.getIntent(), "GENERAL_HELP");
        switch (intent) {
            case "SEARCH_PRODUCTS":
                return runSearchPlan(plan, userMessage);
            case "LOOKUP_ORDERS":
                return runOrderLookupPlan(plan, userMessage, authentication);
            case "ADD_TO_CART":
                return runAddToCartPlan(plan, authentication);
            case "PLACE_ORDER":
                return runPlaceOrderPlan(plan, authentication);
            default:
                return generalHelp(userMessage, authentication);
        }
    }

    private ShoppingAssistantResponseDto runSearchPlan(ShoppingAgentPlanDto plan, String userMessage) {
        ShoppingAssistantResponseDto response = new ShoppingAssistantResponseDto();
        response.setIntent("SEARCH_PRODUCTS");
        response.setState("choose_product");
        ShoppingAgentToolCallDto tool = firstToolCall(plan, "SEARCH_PRODUCTS");
        String query = normalizeQuery(tool == null ? null : tool.getQuery());
        String category = normalizeCategory(tool == null ? null : tool.getCategory());
        boolean browseAll = tool != null && Boolean.TRUE.equals(tool.getBrowseAll());
        int limit = tool != null && tool.getLimit() != null && tool.getLimit() > 0 ? Math.min(tool.getLimit(), 6) : 6;
        if (looksLikeBroadFruitSearch(userMessage, query, category)) {
            query = firstNonBlank(query, "fruit");
            browseAll = false;
            limit = Math.min(limit, 6);
        }
        List<ItemDto> matches = browseAll
                ? loadAllItems(limit)
                : (!query.isBlank() ? searchItems(query, category, limit)
                : (!category.isBlank() ? loadItemsByCategory(category, limit) : loadAllItems(limit)));
        response.setResolvedQuery(displaySearchLabel(query, category, browseAll, userMessage));
        response.setReply(matches.isEmpty()
                ? "I could not find matching products."
                : "I found " + matches.size() + " product" + (matches.size() == 1 ? "" : "s") + ". Pick one and I can add it to your cart or place the order.");
        if (matches.isEmpty()) {
            response.setState("empty");
            response.getActions().add(action("Browse home", "/index.html", "navigate"));
            return response;
        }
        response.setItems(matches.stream().map(this::toAssistantItem).collect(Collectors.toList()));
        response.getActions().add(action("Browse all products", "/index.html", "navigate"));
        response.getActions().add(action("View cart", "/cart.html", "navigate"));
        return response;
    }

    private boolean looksLikeBroadFruitSearch(String userMessage, String query, String category) {
        String lower = firstNonBlank(userMessage, "").toLowerCase(Locale.ROOT);
        String merged = (firstNonBlank(query, "") + " " + firstNonBlank(category, "")).toLowerCase(Locale.ROOT);
        return containsAny(lower, "fruit", "fruits")
                || containsAny(merged, "fruit", "fruits")
                || containsAny(lower, "any type", "any fruits", "any fruit", "fresh fruit", "fresh fruits");
    }

    private ShoppingAssistantResponseDto runOrderLookupPlan(ShoppingAgentPlanDto plan, String userMessage, Authentication authentication) {
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
        String requestedOrderNumber = firstNonBlank(firstToolOrderNumber(plan), extractOrderNumber(userMessage));
        List<OrderDto> matches = filterOrders(orders, plan, requestedOrderNumber, userMessage);
        if (matches.isEmpty()) {
            response.setState("empty");
            response.setReply(requestedOrderNumber.isBlank()
                    ? "I couldn't find a matching order."
                    : "I couldn't find order " + requestedOrderNumber + ".");
            response.getActions().add(action("View account", "/account.html", "navigate"));
            return response;
        }
        response.setOrders(matches.stream().map(this::toAssistantOrder).collect(Collectors.toList()));
        response.setState("success");
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

    private ShoppingAssistantResponseDto runAddToCartPlan(ShoppingAgentPlanDto plan, Authentication authentication) {
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
        ItemDto item = resolveSingleItemFromPlan(plan, conversationKey(authentication));
        if (item == null) {
            List<ItemDto> candidates = resolveCandidatesFromPlan(plan, 3);
            if (candidates.isEmpty()) {
                response.setState("empty");
                response.setReply("I could not find that product to add it to your cart.");
                response.getActions().add(action("Browse products", "/index.html", "navigate"));
                return response;
            }
            if (candidates.size() > 1) {
                response.setState("clarification");
                response.setReply("I found a few matches. Pick one and I can add it to your cart.");
                response.setItems(candidates.stream().map(this::toAssistantItem).collect(Collectors.toList()));
                return response;
            }
            item = candidates.get(0);
        }
        addItemToCart(customerId, token, item, resolveQuantityFromPlan(plan));
        response.setReply("I added " + resolveQuantityFromPlan(plan) + " x " + item.getItemName() + " to your cart.");
        response.getItems().add(toAssistantItem(item));
        response.getActions().add(action("Go to cart", "/cart.html", "navigate"));
        response.getActions().add(action("Checkout", "/checkout.html", "navigate"));
        return response;
    }

    private ShoppingAssistantResponseDto runPlaceOrderPlan(ShoppingAgentPlanDto plan, Authentication authentication) {
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
            response.getActions().add(action("Sign in", "/signin.html", "navigate"));
            return response;
        }

        ItemDto item = resolveSingleItemFromPlan(plan, conversationKey(authentication));
        if (item == null) {
            List<ItemDto> candidates = resolveCandidatesFromPlan(plan, 4);
            if (candidates.isEmpty()) {
                response.setState("empty");
                response.setReply("I could not find the product you wanted to buy.");
                response.getActions().add(action("Browse products", "/index.html", "navigate"));
                return response;
            }
            if (candidates.size() > 1) {
                response.setReply("I found a few matches. Pick one and I can place the order.");
                response.setItems(candidates.stream().map(this::toAssistantItem).collect(Collectors.toList()));
                response.getActions().add(action("Browse products", "/index.html", "navigate"));
                return response;
            }
            item = candidates.get(0);
        }

        AccountDto account = accountService.getCurrentAccount(authentication.getName());
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

        OrderDto placedOrder = placeOrderForItem(customerId, token, item, resolveQuantityFromPlan(plan), shippingAddress, paymentMethod);
        response.setOrderNumber(placedOrder.getOrderNumber());
        response.setCheckoutUrl("/order-status.html?orderNumber=" + urlEncode(placedOrder.getOrderNumber()));
        response.setOrderStatus("PROCESSING");
        response.setOrders(Collections.singletonList(toAssistantOrder(placedOrder, "PROCESSING")));
        response.getItems().add(toAssistantItem(item));
        response.setReply("Your order is processing now. I’ll update the order record when the payment event syncs back.");
        response.getActions().add(action("View order", response.getCheckoutUrl(), "navigate"));
        response.getActions().add(action("Continue shopping", "/index.html", "navigate"));
        return response;
    }

    private ShoppingAssistantResponseDto generalHelp(String userMessage, Authentication authentication) {
        ShoppingAssistantResponseDto response = new ShoppingAssistantResponseDto();
        response.setIntent("GENERAL_HELP");
        response.setState("idle");
        response.setReply(isAuthenticated(authentication)
                ? "I can browse products, search products, add items to your cart, place an order, and look up your orders."
                : "I can browse products and search products. Sign in if you want cart, checkout, or order lookup help.");
        List<ItemDto> matches = loadAllItems(4);
        if (!matches.isEmpty()) {
            response.setItems(matches.stream().map(this::toAssistantItem).collect(Collectors.toList()));
        }
        response.getActions().add(action("Browse all products", "/index.html", "navigate"));
        return response;
    }

    private List<ItemDto> resolveCandidatesFromPlan(ShoppingAgentPlanDto plan, int limit) {
        ShoppingAgentToolCallDto tool = firstToolCall(plan, "SEARCH_PRODUCTS");
        if (tool == null) {
            tool = firstToolCall(plan, "ADD_TO_CART");
        }
        if (tool == null) {
            tool = firstToolCall(plan, "PLACE_ORDER");
        }
        if (tool == null) {
            return new ArrayList<>();
        }
        if (tool.getSku() != null && !tool.getSku().isBlank()) {
            ItemDto item = getItemBySku(tool.getSku());
            if (item == null) {
                return new ArrayList<>();
            }
            return Collections.singletonList(item);
        }
        String query = normalizeQuery(tool.getQuery());
        String category = normalizeCategory(tool.getCategory());
        boolean browseAll = Boolean.TRUE.equals(tool.getBrowseAll());
        int useLimit = tool.getLimit() != null && tool.getLimit() > 0 ? tool.getLimit() : limit;
        if (browseAll) {
            return loadAllItems(useLimit);
        }
        if (!query.isBlank()) {
            return searchItems(query, category, useLimit);
        }
        if (!category.isBlank()) {
            return loadItemsByCategory(category, useLimit);
        }
        return new ArrayList<>();
    }

    private ItemDto resolveSingleItemFromPlan(ShoppingAgentPlanDto plan, String conversationKey) {
        List<ItemDto> items = resolveCandidatesFromPlan(plan, 4);
        if (items.isEmpty()) {
            String memorySku = memoryService.recentSelectedSku(conversationKey).orElse("");
            if (!memorySku.isBlank()) {
                return getItemBySku(memorySku);
            }
            return null;
        }
        if (items.size() == 1) {
            return items.get(0);
        }
        ShoppingAgentToolCallDto tool = firstToolCall(plan, "PLACE_ORDER");
        if (tool != null && tool.getSku() != null && !tool.getSku().isBlank()) {
            ItemDto item = getItemBySku(tool.getSku());
            return item != null ? item : items.get(0);
        }
        return null;
    }

    private ShoppingAssistantResponseDto addToCartDirect(ItemDto item, Authentication authentication) {
        ShoppingAssistantResponseDto response = new ShoppingAssistantResponseDto();
        response.setIntent("ADD_TO_CART");
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
        addItemToCart(customerId, token, item, 1);
        response.setState("success");
        response.setReply("I added " + item.getItemName() + " to your cart.");
        response.getItems().add(toAssistantItem(item));
        response.getActions().add(action("Go to cart", "/cart.html", "navigate"));
        return response;
    }

    private ShoppingAssistantResponseDto placeOrderDirect(ItemDto item, int quantity, Authentication authentication) {
        ShoppingAssistantResponseDto response = new ShoppingAssistantResponseDto();
        response.setIntent("PLACE_ORDER");
        if (!isAuthenticated(authentication)) {
            response.setRequiresSignIn(true);
            response.setState("needs_sign_in");
            response.setReply("Sign in first so I can place an order for you.");
            response.getActions().add(action("Sign in", "/signin.html", "navigate"));
            return response;
        }

        AccountDto account = accountService.getCurrentAccount(authentication.getName());
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
        String token = bearerToken(authentication);
        Long customerId = currentCustomerId(authentication);
        if (token == null || customerId == null) {
            response.setRequiresSignIn(true);
            response.setState("needs_sign_in");
            response.setReply("I need your signed-in session before I can place an order.");
            return response;
        }

        OrderDto placedOrder = placeOrderForItem(customerId, token, item, quantity, shippingAddress, paymentMethod);
        response.setOrderNumber(placedOrder.getOrderNumber());
        response.setCheckoutUrl("/order-status.html?orderNumber=" + urlEncode(placedOrder.getOrderNumber()));
        response.setOrderStatus("PROCESSING");
        response.setItems(Collections.singletonList(toAssistantItem(item)));
        response.setOrders(Collections.singletonList(toAssistantOrder(placedOrder, "PROCESSING")));
        response.setState("processing");
        response.setReply("Your order is processing now. I’ll update the order record when the payment event syncs back.");
        response.getActions().add(action("View order", response.getCheckoutUrl(), "navigate"));
        response.getActions().add(action("Continue shopping", "/index.html", "navigate"));
        return response;
    }

    private OrderDto placeOrderForItem(Long customerId,
                                       String token,
                                       ItemDto item,
                                       int quantity,
                                       AddressSnapshotDto shippingAddress,
                                       StoredPaymentMethodDto paymentMethod) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("customerId", customerId);
        request.put("currencyCode", firstNonBlank(item.getCurrencyCode(), "USD"));
        request.put("taxAmount", "0.00");
        request.put("createRequestId", "agent-order-" + System.currentTimeMillis());
        request.putPOJO("shippingAddress", shippingAddress);
        request.putPOJO("billingAddress", shippingAddress);
        request.putPOJO("paymentMethod", paymentMethod.getPaymentMethodType() == null ? PaymentMethod.CREDIT_CARD : paymentMethod.getPaymentMethodType());

        ArrayNode items = request.putArray("items");
        ObjectNode orderItem = items.addObject();
        orderItem.put("itemId", item.getSku() + "::SHIPPING");
        orderItem.put("sku", item.getSku());
        orderItem.put("itemName", item.getItemName());
        orderItem.put("upc", item.getUpc());
        orderItem.put("quantity", quantity);
        orderItem.put("unitPrice", item.getUnitPrice() == null ? "0.00" : item.getUnitPrice().toPlainString());
        orderItem.put("lineTotal", defaultMoney(item.getUnitPrice()).multiply(BigDecimal.valueOf(quantity)).toPlainString());

        try {
            OrderDto draftOrder = readValue(sendJson(orderServiceUrl("/api/v1/shopping/orders"), request, token), OrderDto.class);
            return readValue(sendRequest(orderServiceUrl("/api/v1/shopping/orders/" + urlEncode(draftOrder.getOrderNumber()) + "/place"), token, "POST"), OrderDto.class);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to place order", ex);
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

    private ShoppingAgentToolCallDto firstToolCall(ShoppingAgentPlanDto plan, String tool) {
        if (plan == null || plan.getToolCalls() == null) {
            return null;
        }
        return plan.getToolCalls().stream()
                .filter(call -> tool.equalsIgnoreCase(call.getTool()))
                .findFirst()
                .orElse(null);
    }

    private String firstToolOrderNumber(ShoppingAgentPlanDto plan) {
        ShoppingAgentToolCallDto call = firstToolCall(plan, "LOOKUP_ORDERS");
        if (call == null) {
            return "";
        }
        return firstNonBlank(call.getOrderNumber(), "");
    }

    private String conversationKey(Authentication authentication) {
        if (authentication == null) {
            return "guest";
        }
        Long customerId = currentCustomerId(authentication);
        if (customerId != null) {
            return "customer:" + customerId;
        }
        return "user:" + firstNonBlank(authentication.getName(), "guest");
    }

    private ShoppingAssistantOrderDto toAssistantOrder(OrderDto order) {
        return toAssistantOrder(order, order == null || order.getStatus() == null ? "" : order.getStatus().name());
    }

    private ShoppingAssistantOrderDto toAssistantOrder(OrderDto order, String statusOverride) {
        ShoppingAssistantOrderDto assistantOrder = new ShoppingAssistantOrderDto();
        assistantOrder.setOrderNumber(order.getOrderNumber());
        assistantOrder.setStatus(firstNonBlank(statusOverride, order.getStatus() == null ? "" : order.getStatus().name()));
        assistantOrder.setStatusReason(order.getStatusReason());
        assistantOrder.setTotalAmount(order.getTotalAmount());
        assistantOrder.setCurrencyCode(firstNonBlank(order.getCurrencyCode(), "USD"));
        assistantOrder.setCreatedAt(order.getCreatedAt());
        assistantOrder.setUpdatedAt(order.getUpdatedAt());
        assistantOrder.setPaymentReference(order.getPaymentReference());
        assistantOrder.setOrderUrl("/order-status.html?orderNumber=" + urlEncode(order.getOrderNumber()));
        return assistantOrder;
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

    private List<OrderDto> filterOrders(List<OrderDto> orders, ShoppingAgentPlanDto plan, String requestedOrderNumber, String userMessage) {
        if (orders == null || orders.isEmpty()) {
            return new ArrayList<>();
        }
        if (requestedOrderNumber != null && !requestedOrderNumber.isBlank()) {
            return orders.stream()
                    .filter(order -> requestedOrderNumber.equalsIgnoreCase(order.getOrderNumber()))
                    .limit(1)
                    .collect(Collectors.toList());
        }
        DateRange dateRange = extractDateRange(plan);
        if (dateRange != null) {
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
        return orders.stream().limit(3).collect(Collectors.toList());
    }

    private DateRange extractDateRange(ShoppingAgentPlanDto plan) {
        ShoppingAgentToolCallDto tool = firstToolCall(plan, "LOOKUP_ORDERS");
        if (tool == null) {
            return null;
        }
        String startText = firstNonBlank(tool.getStartDate(), "");
        String endText = firstNonBlank(tool.getEndDate(), "");
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

    private ItemDto chooseBestCandidate(List<ItemDto> candidates, String sku, String query) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        if (sku != null && !sku.isBlank()) {
            for (ItemDto item : candidates) {
                if (sku.equalsIgnoreCase(item.getSku())) {
                    return item;
                }
            }
        }
        if (query != null && !query.isBlank()) {
            String normalized = query.toLowerCase(Locale.ROOT);
            for (ItemDto item : candidates) {
                if (item.getItemName() != null && item.getItemName().toLowerCase(Locale.ROOT).contains(normalized)) {
                    return item;
                }
            }
        }
        return candidates.get(0);
    }

    private List<ItemDto> resolveCandidateProducts(ShoppingAgentPlanDto plan, int limit) {
        ShoppingAgentToolCallDto tool = firstToolCall(plan, "SEARCH_PRODUCTS");
        if (tool == null) {
            tool = firstToolCall(plan, "PLACE_ORDER");
        }
        if (tool == null) {
            tool = firstToolCall(plan, "ADD_TO_CART");
        }
        if (tool == null) {
            return new ArrayList<>();
        }
        if (tool.getSku() != null && !tool.getSku().isBlank()) {
            ItemDto item = getItemBySku(tool.getSku());
            if (item == null) {
                return new ArrayList<>();
            }
            return Collections.singletonList(item);
        }
        String query = normalizeQuery(tool.getQuery());
        String category = normalizeCategory(tool.getCategory());
        boolean browseAll = Boolean.TRUE.equals(tool.getBrowseAll());
        int useLimit = tool.getLimit() != null && tool.getLimit() > 0 ? tool.getLimit() : limit;
        if (browseAll) {
            return loadAllItems(useLimit);
        }
        if (!query.isBlank() || !category.isBlank()) {
            return searchItems(query, category, useLimit);
        }
        return new ArrayList<>();
    }

    private ItemDto getItemBySku(String sku) {
        if (sku == null || sku.isBlank()) {
            return null;
        }
        try {
            JsonNode node = readJson(sendRequest(itemServiceUrl("/api/v1/shopping/items/sku/" + urlEncode(sku)), null, "GET"));
            return objectMapper.treeToValue(node, ItemDto.class);
        } catch (Exception ex) {
            return null;
        }
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
                throw new IllegalStateException("OpenAI request failed with status " + response.statusCode() + ": " + response.body());
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

    private int resolveQuantityFromPlan(ShoppingAgentPlanDto plan) {
        ShoppingAgentToolCallDto tool = firstToolCall(plan, "PLACE_ORDER");
        if (tool == null || tool.getQuantity() == null || tool.getQuantity() < 1) {
            return 1;
        }
        return tool.getQuantity();
    }

    private int resolveQuantityFromSelection(ShoppingAssistantRequestDto requestDto) {
        return 1;
    }

    private BigDecimal defaultMoney(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private String normalizeQuery(String query) {
        return firstNonBlank(query, "").trim();
    }

    private String normalizeCategory(String category) {
        return firstNonBlank(category, "").trim();
    }

    private String displaySearchLabel(String query, String category, boolean browseAll, String userMessage) {
        if (browseAll) {
            return "all products";
        }
        if (!query.isBlank()) {
            return query;
        }
        if (!category.isBlank()) {
            return category;
        }
        return firstNonBlank(userMessage, "products");
    }

    private String extractOrderNumber(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        Matcher matcher = ORDER_NUMBER_PATTERN.matcher(text);
        return matcher.find() ? matcher.group(1).toUpperCase(Locale.ROOT) : "";
    }

    private boolean containsAny(String text, String... phrases) {
        for (String phrase : phrases) {
            if (text.contains(phrase)) {
                return true;
            }
        }
        return false;
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

    private static class DateRange {
        private final Instant start;
        private final Instant end;

        private DateRange(Instant start, Instant end) {
            this.start = start;
            this.end = end;
        }
    }
}
