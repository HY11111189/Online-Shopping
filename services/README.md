# Service Split

This directory is the beginning of the real service split that was missing from the original single-app layout.

## Target Services

- `account-service`
  - account APIs
  - sign in / sign up
  - frontend static pages
  - MySQL
- `item-service`
  - item metadata
  - inventory
  - MongoDB
- `order-service`
  - cart
  - order lifecycle
  - Cassandra
- `payment-service`
  - payment and refund flows
  - MySQL
- `gateway-service`
  - single browser/API entrypoint
  - routes frontend and API traffic to the right service
- `shared-lib`
  - Feign clients
  - shared inter-service DTOs and enums
  - shared exception contracts

## Auth

- The authentication server is implemented inside `account-service`.
- `item-service`, `order-service`, and `payment-service` are resource servers.
- `gateway-service` is the single browser entrypoint and routes traffic to the right backend service.

## DTO Rule

- Service-local DTOs stay inside their own service.
  - Example: account page request/response models stay in `account-service`.
  - Example: cart request models stay in `order-service`.
- Only DTOs and enums exchanged between services belong in `shared-lib`.
  - Example: `ItemDto`, `InventoryDto`, `OrderDto`, `OrderLineItemDto`, `PaymentStatus`.
- Persistence entities stay inside the service that owns the data store.

This means:

- `shared-lib` is for Feign contracts and shared error types only.
- Business entities, repositories, and service-internal request payloads do not go into `shared-lib`.

## Inventory Ownership

- `item-service` is the single owner of inventory truth.
- `order-service` may read catalog data, but it does not mutate inventory directly.
- Inventory changes happen through internal item-service adjustment calls:
  - `PURCHASE`
  - `RESTOCK`

Atomic inventory behavior:

- All inventory mutations happen inside `item-service`.
- `item-service` applies stock changes with an atomic Mongo update.
- Each inventory adjustment carries an `operationId`.
- Processed `operationId` values are recorded with the item inventory, so duplicate deliveries of the same operation do not double-apply stock changes.

Current simplified lifecycle:

- Cart and checkout only read availability
- Order create/update do not hold stock
- Payment captured / place-order completion: purchase stock atomically
- Payment refunded or cancel-after-payment: restock stock

This is the contract Kafka will build on later, so the service ownership and idempotency shape do not need another redesign.

## Ports

- account-service: `8081`
- item-service: `8082`
- order-service: `8083`
- payment-service: `8084`
- gateway-service: `8080`

## One-Click Startup

```bash
cd services
docker-compose -f docker-compose.yml up --build
```

Or:

```bash
cd services
sh start-all.sh
```

This starts:

- MySQL for account and payment
- MongoDB for item
- Cassandra for order
- Kafka for payment/order asynchronous events
- account, item, order, payment, and gateway services

Recommended browser entrypoint:

- `http://localhost:8080`

## Local Dev Loop

Use this when you want to change code and retest without rebuilding Docker images.

All Maven commands below must be run from the `services/` directory:

```bash
cd springboot-shopping/services
```

Bootstrap the shared module once before running any service:

```bash
sh bootstrap-local.sh
```

Start only the infrastructure once:

```bash
cd services
sh start-infra.sh
```

This script first removes any old compose containers for the infra services, then recreates them.
It also waits until MySQL, MongoDB, Cassandra, and Kafka are reachable before returning.

If you want to clean everything manually first, run:

```bash
cd services
sh cleanup-infra.sh
```

Then run the services from source in separate terminals:

```bash
cd services
cd account-service
mvn spring-boot:run
```

```bash
cd services
cd item-service
mvn spring-boot:run
```

```bash
cd services
cd order-service
mvn spring-boot:run
```

```bash
cd services
cd payment-service
mvn spring-boot:run
```

If you want the browser entrypoint, also run:

```bash
cd services
cd gateway-service
mvn spring-boot:run
```

Notes:

- The services already default to `localhost` ports in their `application.properties`.
- You only need to restart the service you changed.
- If you change `shared-lib`, restart the services that depend on it.
- If `shared-lib` changes, rerun `sh bootstrap-local.sh` before starting services again.
- Kafka uses `localhost:29092` when services run from source, and `kafka:9092` when services run in Docker.
- Use `http://localhost:8080` for the gateway or `http://localhost:8081` through `http://localhost:8084` for direct service testing.

## Kafka

Kafka is used for asynchronous order-to-payment workflow propagation.

Flow:

- `order-service` places the order and publishes `OrderPlacedEvent`
- `payment-service` consumes that event and initializes the payment workflow for the order
- when payment is captured or refunded, `payment-service` synchronously calls `order-service`
- `order-service` updates order state and applies the inventory mutation at that point

Reliability rule:

- the order-placement event is published once per successful order creation
- payment callbacks are idempotent by payment reference and payment status
- `order-service` stores processed payment sync IDs on the order row to avoid double-applying inventory

Inventory rule under concurrency:

- `item-service` is the only inventory owner
- stock is decremented atomically only when purchase completion is processed
- if 1000 customers try to buy 50 units, only the first successful atomic inventory updates succeed
- the rest fail at the item-service inventory check, preventing oversell
