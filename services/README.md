# Overview
The project is split into separate Spring Boot services so each part of the store has a clear boundary:
- `account-service` handles accounts, authentication, and user profile data
- `item-service` handles catalog data, product search, and inventory
- `order-service` handles cart and order lifecycle
- `payment-service` handles payment and refund workflows
- `gateway-service` is the browser/API entrypoint that routes requests to the right backend
- `shared-lib` contains DTOs, enums, and Feign contracts shared across services

Each service owns its own code and data. Services talk to each other over HTTP/Feign instead of sharing one large application layer.


# Services
- `account-service`
  - account APIs
  - sign in / sign up
- `item-service`
  - item metadata
  - inventory
- `order-service`
  - cart
  - synchronous checkout
  - order lifecycle
- `payment-service`
  - payment and refund flows
  - publishes payment-result events to Kafka
- `gateway-service`
  - single browser/API entrypoint
  - routes frontend and API traffic to the right service
- `shared-lib`
  - Feign clients
  - shared inter-service DTOs and enums
  - shared Kafka event DTOs
  - shared exception contracts


# Ports
- account-service: `8081`
- item-service: `8082`
- order-service: `8083`
- payment-service: `8084`
- gateway-service: `8080`


# Database 
Each business service uses its own database so the data model stays isolated:
- `account-service` uses `MySQL`
  - user accounts
  - sign in / sign up data
  - saved addresses and payment methods
- `item-service` uses `MongoDB`
  - product catalog
  - inventory records
  - search indexes and item attributes
- `order-service` uses `Cassandra`
  - carts
  - orders
  - order state transitions
- `payment-service` uses `MySQL`
  - payment records
  - refund records
  - payment status updates


# Inter-Service Communication
The services communicate with each other through OpenFeign for request/response calls and Kafka for payment-result propagation.

Why Feign is used:
- it gives typed Java clients instead of manual HTTP code
- it keeps internal service-to-service calls separate from public browser APIs
- it makes the dependencies explicit in code

Where the clients live:
- [ItemServiceClient.java](/Users/hhhhh1/Desktop/Training/Project/springboot-shopping/services/shared-lib/src/main/java/com/chuwa/shopping/client/ItemServiceClient.java)
- [AccountServiceClient.java](/Users/hhhhh1/Desktop/Training/Project/springboot-shopping/services/shared-lib/src/main/java/com/chuwa/shopping/client/AccountServiceClient.java)
- [OrderServiceClient.java](/Users/hhhhh1/Desktop/Training/Project/springboot-shopping/services/shared-lib/src/main/java/com/chuwa/shopping/client/OrderServiceClient.java)
- [PaymentServiceClient.java](/Users/hhhhh1/Desktop/Training/Project/springboot-shopping/services/shared-lib/src/main/java/com/chuwa/shopping/client/PaymentServiceClient.java)

Communication:
- `order-service` calls `item-service` through Feign to load item details and adjust inventory
- `order-service` calls `account-service` through Feign for checkout profile data
- `order-service` calls `payment-service` through Feign for synchronous payment capture
- `payment-service` calls `order-service` through Feign to sync refund/cancel results
- `payment-service` publishes payment-result events to Kafka
- `order-service` consumes payment-result events from Kafka to keep order state in sync

Where Feign is enabled:
- [OrderServiceApplication.java](/Users/hhhhh1/Desktop/Training/Project/springboot-shopping/services/order-service/src/main/java/com/chuwa/shopping/orderservice/OrderServiceApplication.java)
- [PaymentServiceApplication.java](/Users/hhhhh1/Desktop/Training/Project/springboot-shopping/services/payment-service/src/main/java/com/chuwa/shopping/paymentservice/PaymentServiceApplication.java)

Public routes vs internal routes:
- public browser/API routes go through the gateway, like `/api/v1/shopping/orders/**`
- internal service routes use `/internal/api/v1/...`
- those routes are for service-to-service calls only and are reached through Feign clients, not direct browser access
This keeps the public API stable while allowing services to call each other without exposing internal details to the frontend.

DTO Rule
- Service-local DTOs stay inside their own service.
  - Example: account page request/response models stay in `account-service`.
  - Example: cart request models stay in `order-service`.
- Only DTOs and enums exchanged between services belong in `shared-lib`.
  - Example: `ItemDto`, `InventoryDto`, `OrderDto`, `OrderLineItemDto`, `PaymentStatus`.
- Persistence entities stay inside the service that owns the data store.

This means:
- `shared-lib` is for Feign contracts and shared error types only.
- Business entities, repositories, and service-internal request payloads do not go into `shared-lib`.
- Shared checkout DTOs also live here now, including `PaymentRequestDto` and `PaymentProcessingResultDto`.


# API Contract
The app exposes REST JSON APIs through the gateway on `http://localhost:8080`.

General rules:
- requests and responses use JSON
- public browser/API traffic goes through the gateway
- protected routes use `Authorization: Bearer <jwt>`
- public routes use `/api/v1/...`
- internal service-to-service routes use `/internal/api/v1/...`

## Public APIs
| Service | Method | Path | Purpose |
| --- | --- | --- | --- |
| account | POST | `/api/v1/auth/signin` | sign in and receive a JWT |
| account | POST | `/api/v1/auth/signup` | create a new user account |
| account | GET | `/api/v1/shopping/accounts/{accountId}` | get an account by id |
| account | GET | `/api/v1/shopping/accounts/me` | get the current signed-in account |
| account | POST | `/api/v1/shopping/assistant/chat` | shopping assistant chat |
| item | GET | `/api/v1/shopping/items` | list all items |
| item | GET | `/api/v1/shopping/items/search` | search items by text, category, brand, or stock |
| item | GET | `/api/v1/shopping/items/sku/{sku}` | get an item by SKU |
| item | POST | `/api/v1/shopping/items` | create an item |
| item | PUT | `/api/v1/shopping/items/{itemId}` | update an item |
| order | GET | `/api/v1/shopping/carts/{customerId}` | get the cart for a customer |
| order | POST | `/api/v1/shopping/carts/{customerId}/items` | add an item to cart |
| order | PUT | `/api/v1/shopping/carts/{customerId}/items/{itemId}` | update a cart item |
| order | DELETE | `/api/v1/shopping/carts/{customerId}/items/{itemId}` | remove a cart item |
| order | POST | `/api/v1/shopping/carts/{customerId}/checkout` | checkout the cart |
| order | POST | `/api/v1/shopping/orders` | create a draft order |
| order | POST | `/api/v1/shopping/orders/{orderNumber}/place` | place a draft order |
| order | PUT | `/api/v1/shopping/orders/{orderNumber}` | update an order |
| order | POST | `/api/v1/shopping/orders/{orderNumber}/cancel` | cancel an order |
| order | GET | `/api/v1/shopping/orders/{orderNumber}` | get an order by number |
| order | GET | `/api/v1/shopping/orders/customers/{customerId}` | list orders for a customer |
| payment | POST | `/api/v1/shopping/payments` | submit a payment |
| payment | PUT | `/api/v1/shopping/payments/{paymentNumber}` | update payment status |
| payment | POST | `/api/v1/shopping/payments/{paymentNumber}/refund` | refund a payment |
| payment | POST | `/api/v1/shopping/payments/{paymentNumber}/cancel` | cancel a payment |
| payment | GET | `/api/v1/shopping/payments/{paymentNumber}` | get payment details |

## Internal APIs
| Caller | Method | Path | Purpose |
| --- | --- | --- | --- |
| order-service | GET | `/internal/api/v1/shopping/items/sku/{sku}` | load authoritative item data |
| order-service | POST | `/internal/api/v1/shopping/items/sku/{sku}/inventory/adjustments` | update inventory after order/payment events |
| order-service | POST | `/internal/api/v1/shopping/payments/process` | synchronous payment capture during checkout through `PaymentServiceClient` |
| payment-service | POST | `/internal/api/v1/shopping/payments/process` | internal synchronous payment processing entrypoint |
| payment-service | GET | `/internal/api/v1/shopping/orders/{orderNumber}` | load the order before payment |
| payment-service | POST | `/internal/api/v1/shopping/orders/{orderNumber}/payment` | sync payment status back to order-service |

## Response Shapes
- authentication endpoints return token/login DTOs
- item APIs return item and inventory DTOs
- order APIs return cart and order DTOs
- payment APIs return payment DTOs
- assistant chat returns a structured assistant response with reply text, items, actions, and optional order metadata
- synchronous checkout returns the final order state immediately, while Kafka only carries the payment-result event back to `order-service` for final-state sync


# Spring Security and Auth
- `account-service` is the authentication server for the whole app.
- When a user signs in, it checks the username and password, then returns a signed JWT bearer token.
- The frontend stores that token and sends it as `Authorization: Bearer <token>` on protected requests.
- `item-service`, `order-service`, and `payment-service` run as JWT resource servers, so they validate the token before allowing access to protected APIs.
- `gateway-service` sits in front as the browser entrypoint and forwards each request to the correct backend service.

Key files:
- `OAuth2AuthorizationServerConfig` sets the security rules and JWT setup for `account-service`
- `JwtTokenService` creates the signed token after login
- `CustomUserDetailsService` loads the user from the database during login
- `RsaKeyProperties` creates or loads the RSA keys used to sign and verify tokens
- `AuthController` handles the core sign-in and basic sign-up flow
- `ItemSecurityConfig`, `OrderSecurityConfig`, and `PaymentSecurityConfig` protect the other services as JWT resource servers

Helper or optional files:
- `AuthOAuth2Controller` shows OAuth2 login/token info and examples
- `KeyInfoController` exposes key metadata and public-key info for debugging
- `OAuth2UserController` shows token/user details for debugging

The core auth flow is sign-in, token issuance, and JWT validation. 
The helper controllers are not required for normal app usage.


# Checkout Workflow and Reliability
- `order-service` owns the buy flow in two steps.
- `createOrder` saves a draft order.
- `placeOrder` reserves stock, calls `payment-service` synchronously, and returns the checkout result.
- `payment-service` captures the payment and publishes `PaymentProcessedEvent` to Kafka.
- `order-service` consumes that event to keep the stored order state in sync.
- The frontend gets the yes/no answer from the synchronous `placeOrder` response, while Kafka back-fills the stored order state.

Concurrency rule:
- `item-service` owns the inventory truth, and inventory is changed atomically inside `item-service`.
- each stock adjustment uses a unique `operationId`.
- if many customers try to buy the last units, only the first valid atomic adjustment succeeds.
- the rest fail the stock check, which prevents overselling.
- this is what makes race-buy safe: the stock check and stock update happen together in one service.

At-least-once delivery:
- Kafka redelivers a message until the consumer acknowledges it.
- In this app, `order-service` acknowledges the payment-result record only after it applies the final state update.
- If the consumer throws before `acknowledge()`, Kafka will deliver the same event again.
- this gives us at-least-once delivery for the payment-result event, not exactly-once delivery.

Idempotency:
- `item-service` requires a unique `operationId` for each inventory adjustment, so the same stock change cannot be applied twice.
- `order-service` stores processed payment sync ids, so the same payment result does not update the order twice.
If the payment result event is redelivered, `order-service` skips it because the processed sync id is already stored.

Recovery and compensation:
- If stock reservation fails, the checkout stops immediately and payment is never attempted.
- If stock was already reserved and payment later fails, `order-service` restocks before it marks the order failed.
- Kafka then carries the payment result back to `order-service` so the final order record is consistent even if the response and the event arrive in different orders.

# Product Search
- Category browsing loads directly from MongoDB.
- Text search uses Elasticsearch.
- MongoDB stores the real product records. Elasticsearch stores a searchable copy of those records.
- When an item is created or updated, the app re-indexes it into Elasticsearch.
- Search box requests go to `GET /api/v1/shopping/items/search?q=...`.

Key documents:
- `ItemSearchDocument` defines what product fields are indexed in Elasticsearch and how they are mapped.
- `ItemSearchIndexer` copies MongoDB items into Elasticsearch.
- `ItemSearchService` runs the Elasticsearch text search. This is the search engine logic.
- `ItemSearchRepository` is the Spring Data Elasticsearch repository for saving/searching indexed items.
- `ItemElasticsearchConfig` and `ItemSearchConfig` enable and initialize search support.


# Shopping Assistant
The home page includes a shopping assistant chat widget.

What it can do:
- `SEARCH_PRODUCTS`
  - show matching products
  - after the user picks one, they can optionally add it to cart or place the order
- `PLACE_ORDER`
  - show matching products
  - after the user picks one, the assistant places the order directly
- `LOOKUP_ORDERS`
  - show matching orders by order number or date range
- `GENERAL_HELP`
  - shopping-related questions that don't fit the above intents
  - returns a brief capability summary and sample products
- `OUT_OF_SCOPE`
  - non-shopping questions (weather, politics, coding, general knowledge, etc.)
  - returns a polite message redirecting the user to shopping topics

Example:
```json
{
  "intent": "SEARCH_PRODUCTS",
  "productName": "coffee maker",
  "searchQuery": "coffee maker",
  "sku": "",
  "quantity": 1,
  "category": "",
  "orderNumber": "",
  "startDate": "",
  "endDate": "",
  "browseAll": false
}
```

Backend turns that into a product search request:
```http
GET /api/v1/shopping/items/search?q=coffee%20maker&limit=4
```

```json
{
  "intent": "PLACE_ORDER",
  "productName": "medicine",
  "searchQuery": "medicine",
  "sku": "",
  "quantity": 1,
  "category": "",
  "orderNumber": "",
  "startDate": "",
  "endDate": "",
  "browseAll": false
}
```

Backend turns that into a draft-order create request:
```http
POST /api/v1/shopping/orders
```

Then the backend places that draft order with:
```http
POST /api/v1/shopping/orders/{orderNumber}/place
```

The assistant uses the same synchronous checkout flow as the frontend:
- the assistant classifies the request into JSON
- the backend resolves the product
- `order-service` creates a draft order
- `order-service` places that draft order, reserves stock, and calls `payment-service`
- the final order result is returned to the chat UI
- Kafka is only used afterward to sync the payment result back into `order-service`

```json
{
  "intent": "LOOKUP_ORDERS",
  "productName": "",
  "searchQuery": "",
  "sku": "",
  "quantity": 1,
  "category": "",
  "orderNumber": "",
  "startDate": "2026-04-30",
  "endDate": "2026-05-01",
  "browseAll": false
}
```

Backend turns that into an order lookup request:
```http
GET /api/v1/shopping/orders/customers/{customerId}
```
Then it filters the returned orders by `createdAt` using the date range from the AI JSON.

API contract:
- request: `POST /api/v1/shopping/assistant/chat`
- body: `{ "message": "find me a coffee maker" }`
- response fields:
  - `intent`
  - `reply`
  - `requiresSignIn`
  - `state`
  - `items`
  - `orders`
  - `actions`
  - `orderNumber`
  - `checkoutUrl`

Examples:
```json
{
  "intent": "SEARCH_PRODUCTS",
  "reply": "I found 3 products for \"coffee maker\". Pick one and I can add it to your cart or place the order.",
  "state": "choose_product",
  "items": [{ "sku": "SKU-101", "itemName": "Coffee Maker" }],
  "actions": [{ "label": "Browse all products", "href": "/index.html", "type": "navigate" }]
}
```

```json
{
  "intent": "PLACE_ORDER",
  "reply": "Your order was placed and payment was captured.",
  "state": "processing",
  "orderNumber": "ORD-1234ABCD",
  "checkoutUrl": "/order-status.html?orderNumber=ORD-1234ABCD"
}
```

```json
{
  "intent": "LOOKUP_ORDERS",
  "reply": "Here are the matching orders from your account.",
  "state": "success",
  "orders": [{ "orderNumber": "ORD-1234ABCD", "status": "PAID" }]
}
```


# Shopping Agent
The home page also includes a more autonomous **Shopping Agent** chat widget (the "AG" button). 
Unlike the assistant, which classifies a single intent and returns one result, 
the agent asks OpenAI to produce a full **plan** (an ordered list of tool calls),
and then executes each step in sequence before returning a combined response.

## Key flow

```
User message
    │
    ▼
1. Cache check (ShoppingAgentMemoryService)
   └─ normalized message hit → return cached response immediately (zero API calls)
    │
    ▼ miss
2. OpenAI planning (classifyPlan)
   └─ sends message + rolling clarification summary to OpenAI Responses API
   └─ receives ShoppingAgentPlanDto: intent + ordered toolCalls list + needsClarification flag
    │
    ▼
3. Plan execution (executePlan)
   ├─ needsClarification=true  → return clarification question to user, update rolling summary
   ├─ SEARCH_PRODUCTS          → search item-service, return product cards
   ├─ ADD_TO_CART              → resolve product, POST to order-service cart endpoint
   ├─ PLACE_ORDER              → resolve product, POST create + place to order-service
   ├─ LOOKUP_ORDERS            → GET orders from order-service, filter by date range or order number
   ├─ GENERAL_HELP             → return browse prompt with sample products
   └─ OUT_OF_SCOPE             → politely decline non-shopping questions
    │
    ▼
4. Cache store (ShoppingAgentMemoryService)
   └─ store response keyed by normalized message for future lookups
    │
    ▼
5. Rolling summary update (async, background)
   └─ only updated during a clarification chain; cleared after a round resolves
```

## Planning (classifyPlan)
`ShoppingAgentService.classifyPlan` sends the user message to the **OpenAI Responses API** with a strict JSON schema, 
forcing the model to return a machine-parseable `ShoppingAgentPlanDto`:

```json
{
 
  "reply": "Placing order now.",
  "clarificationQuestion": "",
  "needsClarification": false,
  "toolCalls": [
    { "tool": "SEARCH_PRODUCTS", "query": "coffee maker", "category": "", "quantity": 1, ... },
    { "tool": "PLACE_ORDER",     "sku": "",               "quantity": 1, ... }
  ]
}
```

The instructions injected into OpenAI enforce:
- query must be 1–3 specific product-type keywords (not descriptive phrases like "gift for mum")
- at most 2 clarification rounds; never ask when a product type is already clear
- `OUT_OF_SCOPE` for any non-shopping question

## Execution (executePlan)
`ShoppingAgentService.executePlan` routes on `intent` and runs each tool call in the plan:
- **SEARCH_PRODUCTS** — calls `item-service` search or category endpoint
- **ADD_TO_CART** — resolves a single item then POSTs to `order-service` cart
- **PLACE_ORDER** — resolves a single item, loads account for address + payment, creates a draft order, then places it (two-step: `POST /orders` then `POST /orders/{number}/place`)
- **LOOKUP_ORDERS** — loads all customer orders then filters by AI-provided date range or order number

If `needsClarification=true`, execution is short-circuited and the clarification question is returned directly.

## Memory and caching (ShoppingAgentMemoryService)
| Store | Key | Purpose |
|---|---|---|
| `memoryByKey` deque | normalized user message | cache any response (including clarification) so identical messages return instantly with zero API calls |
| `summaryByKey` string | conversation key | rolling clarification summary injected into the next planning prompt; cleared after each round resolves so one question's context does not bleed into the next |
| `recentSelectedSku` | conversation key | remembers the last product the user interacted with so follow-ups like "buy that one" resolve without a new search |

## Key files
- `account-service/.../agent/service/ShoppingAgentService.java` | main pipeline: cache check → planning → execution → memory store 
- `account-service/.../agent/service/ShoppingAgentMemoryService.java` | in-memory cache, rolling summary, selected-SKU tracking 
- `account-service/.../agent/dto/ShoppingAgentPlanDto.java` | OpenAI plan JSON schema (intent, toolCalls, needsClarification) 
- `account-service/.../agent/dto/ShoppingAgentToolCallDto.java` | individual tool call within the plan (tool, query, category, sku, dates, quantity) 
- `account-service/.../agent/controller/ShoppingAgentController.java` | REST endpoint: `POST /api/v1/shopping/agent/chat` 
- `frontend/src/components/ShoppingAgentChat.jsx` | chat widget: sends messages, renders product/order cards, handles clarification and selection 

## API contract
- request: `POST /api/v1/shopping/agent/chat`
- body: `{ "message": "find me a coffee maker" }` or `{ "selectedAction": "PLACE_ORDER", "selectedSku": "SKU-101", "selectedItemName": "Coffee Maker" }`
- response fields: same as assistant plus `cartItemCount`, `resolvedQuery`

Direct selection (user clicks a product card button) bypasses OpenAI entirely — the agent resolves the item by SKU and jumps straight to cart or order.


# Local Dev Loop
Use this when you want to run services locally without Docker:

```bash
cd services
sh bootstrap-local.sh
sh start-infra.sh
sh scripts/run-service.sh account-service
sh scripts/run-service.sh item-service
sh scripts/run-service.sh order-service
sh scripts/run-service.sh payment-service
sh scripts/run-service.sh gateway-service
```

Notes:
- rerun `bootstrap-local.sh` after `shared-lib` changes
- direct service ports are `8081` to `8084`
- use `http://localhost:8080` for the gateway


# One-Click Startup
Run the full stack with one command:

```bash
cd services
sh start-all.sh
```

Or use Docker Compose directly:
```bash
cd services
docker-compose -f docker-compose.yml up --build
```

Open `http://localhost:8080` in the browser.
