Microservice E-Commerce Project – Detailed Documentation
This document describes the technologies, services, architecture, classes, interfaces, methods, commands, and configuration used in this project.

1. Project Overview
This is a microservices-based e-commerce application (E-Shop). It consists of:

Backend: Multiple Spring Boot services (user, product, order, inventory, payment, notification) that register with Eureka and are accessed through a single API Gateway.
Frontend: A Thymeleaf storefront (port 9999) that talks only to the API Gateway using JWT.
Event-driven: Apache Kafka is used so that when an order is placed, the inventory service consumes the event and decreases stock asynchronously.
Why microservices? Each domain (users, products, orders, inventory, payments) runs in its own service. This allows independent scaling, deployment, and technology choices. The API Gateway provides a single entry point and centralizes JWT validation.

2. Technologies, Frameworks & Why They Are Used
Technology / Framework	Where Used	Why Used
Java 21	All services	LTS, modern language features, strong ecosystem for enterprise apps.
Spring Boot 3.x	All services	Rapid development, auto-configuration, embedded server, production-ready features (actuator, health).
Spring Cloud Gateway	api-gateway	Reactive, non-blocking gateway; route requests to services by path; run global filters (e.g. JWT).
Spring Cloud Netflix Eureka	eureka-server + all backend services	Service discovery: services register themselves; gateway uses lb://service-name to resolve instances.
JWT (jjwt)	user-service (issue), api-gateway (validate)	Stateless auth: login returns a token; gateway validates it for every request without session.
Spring Security	user-service, api-gateway	Protect endpoints; user-service: permit register/login, require JWT for rest; gateway: validate JWT or allow public paths.
Spring Data JPA / Hibernate	user, product, order, inventory, payment	ORM for MySQL; repositories, entities, automatic schema update (ddl-auto=update).
MySQL	user, product, inventory (payment/order as per config)	Relational DB for users, products, categories, orders, inventory, payments.
Apache Kafka	order-service (producer), inventory-service (consumer)	Decouple order placement from inventory update: order service publishes event; inventory consumes and updates stock.
Spring Kafka	order-service, inventory-service	Producer template and @KafkaListener for producing/consuming events.
OpenFeign	order-service	Declarative HTTP client to call inventory-service (e.g. check stock by SKU) before placing order.
Thymeleaf	storefront-service	Server-side HTML templates; integrates with Spring MVC; simple for login, home, order, admin pages.
Bootstrap	storefront-service (templates)	UI layout and components for a responsive storefront and admin panel.
RestTemplate	storefront-service	HTTP client to call API Gateway (login, products, categories, order, admin APIs) with JWT in header.
Lombok	All Java modules	Reduces boilerplate: @Data, @Getter, @Setter, @RequiredArgsConstructor, etc.
Micrometer / Zipkin	Multiple services	Observability: tracing (optional); Zipkin endpoint for distributed traces.
Docker	kafka-infra	Run Kafka in a container via docker-compose without local Kafka install.
3. Architecture (High-Level)
                    ┌─────────────────────────┐
                    │   Storefront (9999)     │  Thymeleaf + Bootstrap
                    │   RestTemplate → Gateway│  Session: token, role
                    └────────────┬────────────┘
                                 │ Authorization: Bearer <JWT>
                                 ▼
                    ┌─────────────────────────┐
                    │   API Gateway (8080)     │  Spring Cloud Gateway
                    │   JwtGatewayFilter       │  Public: /api/users/register, login, /products/images
                    │   Routes by Path         │  Rest: require valid JWT
                    └────────────┬────────────┘
                                 │ lb://user-service, lb://product-service, etc.
                                 ▼
                    ┌─────────────────────────┐
                    │   Eureka (8761)         │  Service discovery
                    └─────────────────────────┘
                                 │
     ┌────────────┬──────────────┼──────────────┬────────────┐
     ▼            ▼              ▼              ▼            ▼
 user-svc    product-svc    order-svc    inventory-svc   payment-svc  notification-svc
 (8086)        (8085)        (8083)         (8081)         (8084)        (8082)
     │            │              │              │
     │            │              │  Kafka       │
     │            │              │  topic:      │
     │            │              │  order-event │
     │            │              └──────────────┘
     │            │                        │
     │            │                        ▼
     │            │                 OrderEventConsumer → updateStock()
Storefront is the only UI; it never calls backend services directly—only the Gateway with JWT.
Gateway validates JWT (except for public URLs), then forwards to the right service using Eureka.
Order flow: User places order → Gateway → order-service → check inventory (Feign) → save order → publish OrderPlacedEvent to Kafka → inventory-service consumes and decreases quantity.
4. Services in Detail
4.1 Eureka Server (Port 8761)
Purpose: Central service registry. All backend microservices register here; the API Gateway uses it to discover instances (lb://service-id).

Key files:

EurekaServerApplication.java – @EnableEurekaServer
application.properties – server.port=8761, no DB
Commands:

cd eureka-server
mvn spring-boot:run
Dashboard: http://localhost:8761

4.2 API Gateway (Port 8080)
Purpose: Single entry point for all API calls. Validates JWT (except public paths) and routes by path to the correct microservice.

Technology: Spring Cloud Gateway (WebFlux), Spring Security not used for request auth—custom JwtGatewayFilter does JWT validation.

Key classes / components:

Class / File	Type	Role
ApiGatewayApplication.java	Main	Spring Boot entry
JwtGatewayFilter.java	GlobalFilter, Ordered	Runs first; allows /api/users/register, /api/users/login, /products/images without token; for others expects Authorization: Bearer <token>, validates via JwtUtil, then forwards
JwtUtil.java	Util	Parse and validate JWT (same secret as user-service)
CustomRouteFilter	AbstractGatewayFilterFactory	Example route filter; adds header X-Route-Header on user-service route
Configuration (application.yml):

server.port: 8080
jwt.secret, jwt.expiration-ms – must match user-service
spring.cloud.gateway.routes – path-based routes to lb://user-service, lb://product-service, etc.
eureka.client.service-url.defaultZone: http://localhost:8761/eureka
Commands:

cd api-gateway
mvn spring-boot:run
4.3 User Service (Port 8086)
Purpose: User registration, login, and JWT issuance. All other services rely on the Gateway for auth; they do not validate JWT themselves.

Technology: Spring Boot Web, Spring Security (stateless, JWT filter), Spring Data JPA, MySQL.

Key classes:

Class	Type	Role
User	Entity	id, name, email, password, phone, roles, createdAt, updatedAt
UserRepository	Interface extends JpaRepository	CRUD, find by email
UserService	Interface	register, login, getById
UserServiceImpl	Service	Register (encode password), login (validate, generate JWT)
UserController	RestController	POST /api/users/register, POST /api/users/login, GET /api/users/{id}
JwtUtil	Component	generateToken(userId, email, role), validateToken
JwtAuthenticationFilter	Filter	Extracts Bearer token, validates, sets SecurityContext
SecurityConfig	Config	Permit /api/users/register, /api/users/login, /actuator/**; rest authenticated
AuthMapper	Mapper	User → LoginResponseDto (includes token, role)
LoginResponseDto, LoginRequestDto, UserCreateRequestDto, UserResponseDto	DTOs	Request/response for login and user
Configuration: application.properties – port 8086, MySQL user_db, app.jwt.secret, app.jwt.expiration-ms, Eureka.

Commands:

cd user-service
mvn spring-boot:run
4.4 Product Service (Port 8085)
Purpose: Products and categories CRUD, image upload, search and filter (by category, price, keyword).

Technology: Spring Boot Web, Spring Data JPA, MySQL. Serves product images from disk (e.g. uploads/products/).

Key classes:

Class	Type	Role
Product	Entity	id, name, description, price, discountPrice, quantity, brand, imageUrl, category (ManyToOne), timestamps
Category	Entity	id, name, etc.
ProductRepository	Interface	JpaRepository + findByCategoryId, findByPriceBetween, findByNameContainingIgnoreCase, searchProducts (JPQL), advanceFilter (keyword, categoryId, minPrice, maxPrice)
CategoryRepository	Interface	JpaRepository
ProductService	Interface	CRUD, getPage, search, filterProducts, advanceFilter, uploadImage
ProductServiceImpl	Service	filterProducts uses advanceFilter with default min/max price when null so category-only filter works
CategoryService / CategoryServiceImpl	Service	Category CRUD
ProductController	RestController	/api/products – POST, GET, GET by id, GET paginated, GET search, GET filter, GET advance-filter, POST upload-image
CategoryController	RestController	/api/categories – POST, GET, GET by id
ImageServeController	RestController	GET /products/images/{fileName} – serve image files
Configuration: MySQL product_db, port 8085, Eureka.

Commands:

cd product-service
mvn spring-boot:run
4.5 Order Service (Port 8083)
Purpose: Place order: check inventory (via Feign), save order, publish OrderPlacedEvent to Kafka so inventory-service can decrease stock.

Technology: Spring Boot Web, Spring Data JPA, Spring Cloud OpenFeign, Spring Kafka. DB for orders (e.g. H2/MySQL as configured).

Key classes:

Class	Type	Role
Order	Entity	order fields (orderNumber, skuCode, quantity, etc.)
OrderRepository	Interface	JpaRepository
OrderService / OrderServiceImpl	Service	PlaceOrder: Feign call to check stock → save order → build OrderPlacedEvent → send to Kafka
OrderController	RestController	POST /api/order – place order
OrderEventProducer	Service	sendOrderEvent(OrderPlacedEvent) – sends to topic order-event with key orderId
OrderPlacedEvent	DTO	eventId, orderId, skuCode, quantity, eventTime
InventoryFeignClient	Interface	@FeignClient("inventory-service") GET /api/inventory/{skuCode} – returns stock info
Configuration: Port 8083, Eureka, Kafka bootstrap servers (e.g. localhost:9092).

Commands:

cd order-service
mvn spring-boot:run
4.6 Inventory Service (Port 8081)
Purpose: Maintain stock per SKU; consume Kafka order-event and decrease quantity; avoid duplicate processing with ProcessedOrder.

Technology: Spring Boot Web, Spring Data JPA, Spring Kafka, MySQL.

Key classes:

Class	Type	Role
Inventory	Entity	skuCode, quantity, etc.
ProcessedOrder	Entity	orderId, processedAt – idempotency for Kafka consumption
InventoryRepository	Interface	findBySkuCode
ProcessedOrderRepository	Interface	existsByOrderId
InventoryService	Service	checkInventory(skuCode), addInventory, updateStock(OrderPlacedEvent) – if already processed skip; else decrease quantity and save ProcessedOrder
OrderEventConsumer	Service	@KafkaListener(topics = "order-event", groupId = "inventory-group") – calls inventoryService.updateStock(event)
OrderPlacedEvent	DTO	Same structure as order-service (package may differ; consumer uses its own event class)
InventoryController	RestController	GET /api/inventory/{skuCode}, POST /api/inventory (add stock)
Configuration: application.yml – Kafka consumer with spring.json.use.type.headers: false, spring.json.value.default.type: com.ecommerce.inventory.event.OrderPlacedEvent (so it deserializes without producer type header). MySQL inventory_db.

Commands:

cd inventory-service
mvn spring-boot:run
4.7 Payment Service (Port 8084)
Purpose: Create payment, get payment by order; Razorpay webhook; optionally confirm/fail order and inventory commit/rollback via Feign to order-service and inventory-service.

Key classes: PaymentController, WebhookController, PaymentService, PaymentGateway (e.g. RozarpayPaymentGateway), OrderClient, InventoryClient, Payment entity, PaymentRepository.

Commands:

cd payment-service
mvn spring-boot:run
4.8 Notification Service (Port 8082)
Purpose: Send notifications (e.g. email). Can consume events (e.g. from Kafka) or be called via API.

Key classes: EmailService, SmsService, NotificationConsumer (if Kafka consumer).

Commands:

cd notification-service
mvn spring-boot:run
4.9 Storefront Service (Port 9999)
Purpose: Web UI for customers (login, register, home with products/categories/pagination, place order) and for admins (dashboard, products, categories, inventory CRUD). Calls only the API Gateway with JWT.

Technology: Spring Boot Web, Thymeleaf, Bootstrap, RestTemplate.

Key classes:

Class	Type	Role
StorefrontApplication	Main	Spring Boot entry
GatewayClient	Component	RestTemplate calls to Gateway: login, register, getProductsPage, getProductsFilteredByCategory, getCategories, checkInventory, placeOrder; admin: getProductById, create/update/delete product/category, addInventory
AuthController	Controller	GET/POST login, register, logout; session stores token and role
HomeController	Controller	GET /, /home – categoryId, page, size; loads categories and products (all or by category), pagination
OrderController	Controller	GET /order, POST /order/place
AdminController	Controller	GET/POST for /admin dashboard, products (list, new, edit, delete), categories (list, new, edit, delete), inventory (add)
AuthInterceptor	Interceptor	Ensures user is logged in (session token); stores SESSION_ROLE for admin check
AdminInterceptor	Interceptor	For /admin/** checks role is ROLE_ADMIN; else redirect
WebMvcConfig	Config	Registers interceptors; auth for protected paths, admin for /admin/**
DTOs	ProductDto, CategoryDto, PageResponse, OrderRequest, OrderResponse, LoginResponse, etc.	Match Gateway API
Templates: layout.html, login.html, register.html, home.html, order.html, admin/dashboard.html, admin/products.html, admin/product-form.html, admin/categories.html, admin/category-form.html, admin/inventory.html.

Configuration: application.yml – port 9999, gateway.base-url: http://localhost:8080.

Commands:

cd storefront-service
mvn spring-boot:run
Open http://localhost:9999. Admin: http://localhost:9999/admin (user must have ROLE_ADMIN in DB).

5. Important Flows
5.1 Login Flow
User submits email/password on storefront → POST to Gateway /api/users/login.
Gateway: path is public → forward to user-service.
user-service: validate credentials, generate JWT (subject=userId, claims: email, role), return LoginResponseDto (token, role, etc.).
Storefront stores token (and role) in session; subsequent requests send Authorization: Bearer <token>.
5.2 Place Order → Inventory Update (Kafka)
User places order from storefront → POST to Gateway /api/order with JWT.
Gateway validates JWT → forwards to order-service.
order-service: Feign call to inventory-service GET /api/inventory/{skuCode} (via Gateway) to check stock; if not in stock throw; else save order, build OrderPlacedEvent (orderId, skuCode, quantity), send to Kafka topic order-event.
inventory-service: OrderEventConsumer receives event, calls updateStock: if orderId already in ProcessedOrder skip (idempotency); else find Inventory by skuCode, decrease quantity, save, save ProcessedOrder.
5.3 Category Filter (Products)
User clicks category on home → storefront requests /home?categoryId=x&page=0&size=12.
HomeController calls GatewayClient.getProductsFilteredByCategory(token, categoryId, page, size) → GET Gateway /api/products/filter?categoryId=x&page=0&size=12.
product-service: filterProducts(categoryId, null, null, page, size) uses default minPrice=0, maxPrice=Double.MAX_VALUE and calls ProductRepository.advanceFilter so category-only filter returns products.
6. Commands Summary
Prerequisites: Java 21, Maven, Docker (for Kafka), MySQL (create DBs: user_db, product_db, inventory_db as per config).

1. Start Kafka:

cd kafka-infra
docker-compose up -d
2. Start services (Eureka first, then Gateway, then others in any order):

cd eureka-server    && mvn spring-boot:run
cd api-gateway      && mvn spring-boot:run
cd user-service     && mvn spring-boot:run
cd product-service  && mvn spring-boot:run
cd order-service    && mvn spring-boot:run
cd inventory-service && mvn spring-boot:run
cd payment-service  && mvn spring-boot:run    # optional
cd notification-service && mvn spring-boot:run # optional
cd storefront-service && mvn spring-boot:run
3. URLs:

Eureka: http://localhost:8761
API Gateway: http://localhost:8080
Storefront: http://localhost:9999
Admin: http://localhost:9999/admin
4. Make user admin (MySQL):

USE user_db;
UPDATE user SET roles = 'ROLE_ADMIN' WHERE email = 'your@email.com';
7. Configuration Summary
Item	Where	Value / Note
JWT secret	user-service, api-gateway	Must be identical (e.g. app.jwt.secret in user-service, jwt.secret in gateway).
JWT expiration	user-service, api-gateway	e.g. 86400000 ms (24 hours).
Eureka URL	All backend + gateway	http://localhost:8761/eureka
Gateway base URL	storefront	http://localhost:8080
Kafka	order-service, inventory-service	bootstrap-servers: localhost:9092; topic: order-event; inventory consumer group: inventory-group.
MySQL	user, product, inventory	Create DBs: user_db, product_db, inventory_db; username/password in application.properties/yml.
Zipkin (optional)	gateway, etc.	endpoint http://localhost:9411/api/v2/spans
8. Project Folder Structure
Microservice Ecommerce Project/
├── api-gateway/           # Gateway, JWT filter, routes
├── eureka-server/         # Eureka server
├── user-service/          # Auth, JWT, users
├── product-service/       # Products, categories, images, filter
├── order-service/         # Orders, Kafka producer, Feign
├── inventory-service/     # Stock, Kafka consumer
├── payment-service/       # Payments, webhook
├── notification-service/  # Notifications
├── storefront-service/    # Thymeleaf UI, GatewayClient
├── kafka-infra/           # docker-compose for Kafka
├── DOCUMENTATION.md       # This file
└── README.md              # Quick start (if present)
9. Interfaces and Methods (Quick Reference)
UserService: register, login, getById
ProductService: create, update, getById, getAll (page), search, filterProducts, advanceFilter, uploadImage
CategoryService: CRUD for categories
OrderService: PlaceOrder
InventoryService: checkInventory, addInventory, updateStock
PaymentService / PaymentGateway: create payment, handle webhook
GatewayClient (storefront): login, register, getProductsPage, getProductsFilteredByCategory, getCategories, checkInventory, placeOrder, admin CRUD methods
Repositories extend JpaRepository and add custom methods (e.g. ProductRepository.advanceFilter, InventoryRepository.findBySkuCode, ProcessedOrderRepository.existsByOrderId). Controllers expose REST endpoints under /api/* as listed in each service section above.

This documentation covers the technologies, architecture, each service’s purpose and key classes/interfaces/methods, main flows, commands to run, and configuration. For code-level details, refer to the source files in each module.
