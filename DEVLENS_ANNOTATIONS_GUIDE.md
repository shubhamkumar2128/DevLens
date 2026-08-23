# CodeLens Annotations — Technical Reference Guide

A comprehensive guide to all DevLens annotations: what they do, how to use them, when to use them, and what output to expect.

---

## Table of Contents

1. [Quick Start](#quick-start)
2. [@ExecutionTime](#executiontime)
3. [@SlowMethod](#slowmethod)
4. [@LogInput](#loginput)
5. [@LogOutput](#logoutput)
6. [@LogException](#logexception)
7. [@Retry](#retry)
8. [@CorrelationId](#correlationid)
9. [@ExternalCall](#externalcall)
10. [@MemoryUsage](#memoryusage)
11. [@ThreadInfo](#threadinfo)
12. [@DevTrace](#devtrace)
13. [Combining Annotations](#combining-annotations)
14. [Configuration Reference](#configuration-reference)
15. [Customization (Serialization & Masking)](#customization)
16. [Architecture Overview](#architecture-overview)

---

## Quick Start

### Maven Dependency

```xml
<dependency>
    <groupId>com.devlens</groupId>
    <artifactId>devlens-spring-boot-starter</artifactId>
    <version>${devlens.version}</version>
</dependency>
```

That's it. Spring Boot auto-configuration handles everything. Just annotate your methods.

### Building the JARs

#### Build all modules (install to local Maven repository)

```bash
mvn clean install
```

This compiles and installs all three modules to your local `~/.m2/repository`:
- `devlens-annotations-1.0.0-SNAPSHOT.jar`
- `devlens-core-1.0.0-SNAPSHOT.jar`
- `devlens-spring-boot-starter-1.0.0-SNAPSHOT.jar`

#### Build without running tests

```bash
mvn clean install -DskipTests
```

#### Build a specific module only

```bash
# Build only the annotations module
mvn clean install -pl devlens-annotations

# Build the starter (and its dependencies)
mvn clean install -pl devlens-spring-boot-starter -am
```

The `-am` (also-make) flag builds required dependency modules automatically.

#### Package JARs without installing to local repo

```bash
mvn clean package
```

JARs are created in each module's `target/` directory:
```
devlens-annotations/target/devlens-annotations-1.0.0-SNAPSHOT.jar
devlens-core/target/devlens-core-1.0.0-SNAPSHOT.jar
devlens-spring-boot-starter/target/devlens-spring-boot-starter-1.0.0-SNAPSHOT.jar
```

### Using the Library in Another Project

After running `mvn clean install`, add the dependency to your Spring Boot application's `pom.xml`:

```xml
<dependency>
    <groupId>com.devlens</groupId>
    <artifactId>devlens-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Then run your application as usual:

```bash
# Using Maven Spring Boot plugin
mvn spring-boot:run

# Or run the packaged JAR
java -jar target/your-app-1.0.0.jar
```

### Running from IDE (IntelliJ / VS Code)

> **Important:** This project is a **library** (multi-module, `<packaging>pom</packaging>` at root). It does NOT have a runnable main class itself. You cannot run it directly.
>
> To test the annotations, create a **separate Spring Boot application** that depends on `devlens-spring-boot-starter` and run that application.

#### Steps:

1. Build and install DevLens locally:
   ```bash
   mvn clean install -DskipTests
   ```

2. Create or open your Spring Boot application project

3. Add the DevLens dependency to that project's `pom.xml`:
   ```xml
   <dependency>
       <groupId>com.devlens</groupId>
       <artifactId>devlens-spring-boot-starter</artifactId>
       <version>1.0.0-SNAPSHOT</version>
   </dependency>
   ```

4. Annotate your methods and run the application:
   ```java
   @RestController
   public class DemoController {

       @GetMapping("/hello")
       @ExecutionTime
       @LogInput
       @LogOutput
       public String hello(@RequestParam String name) {
           return "Hello, " + name;
       }
   }
   ```

5. Run `DemoApplication.main()` from IntelliJ or:
   ```bash
   mvn spring-boot:run
   ```

6. Hit the endpoint and check console logs:
   ```bash
   curl http://localhost:8080/hello?name=World
   ```
   ```
   [DevLens] LogInput class=DemoController method=hello args=[name=World]
   [DevLens] ExecutionTime class=DemoController method=hello executionTime=3ms
   [DevLens] LogOutput class=DemoController method=hello result=Hello, World
   ```

### Minimal Example

```java
@ExecutionTime
@LogInput
public Order createOrder(OrderRequest request) {
    return orderRepository.save(request.toEntity());
}
```

Output:
```
[DevLens] LogInput class=OrderService method=createOrder args=[OrderRequest{customerId=42, items=3}]
[DevLens] ExecutionTime class=OrderService method=createOrder executionTime=23ms
```

---

## @ExecutionTime

**Purpose:** Measures and logs how long a method takes to execute.

### Definition

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExecutionTime {
    long threshold() default 0;  // milliseconds
}
```

### Attributes

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `threshold` | `long` | `0` | If execution time exceeds this value (ms), logs at WARN level. `0` means always log at INFO. |

### Usage Examples

#### Basic — Always log execution time

```java
@ExecutionTime
public List<User> getAllUsers() {
    return userRepository.findAll();
}
```

Output:
```
[DevLens] ExecutionTime class=UserService method=getAllUsers executionTime=45ms
```

#### With threshold — Only warn when slow

```java
@ExecutionTime(threshold = 500)
public Report generateMonthlyReport(int month) {
    // complex aggregation
    return reportEngine.build(month);
}
```

Output when fast (23ms — INFO level):
```
[DevLens] ExecutionTime class=ReportService method=generateMonthlyReport executionTime=23ms
```

Output when slow (1200ms — WARN level):
```
WARN [DevLens] ExecutionTime class=ReportService method=generateMonthlyReport executionTime=1200ms
```

### When to Use

- **API endpoints** — Track response times per handler to find which endpoints are slow
- **Database queries** — Identify queries that need optimization
- **Batch jobs** — Monitor processing duration per batch step
- **Any method you suspect might be a bottleneck**

### Real-World Scenario: SLA Monitoring

```java
@ExecutionTime(threshold = 200)
public PaymentResult processPayment(PaymentRequest request) {
    // SLA requires < 200ms response
    return paymentGateway.charge(request);
}
```

If this method consistently triggers WARN, you know your SLA is at risk before customers complain.

---

## @SlowMethod

**Purpose:** Logs a warning ONLY when execution time exceeds the threshold. Unlike `@ExecutionTime`, it produces no output for fast executions.

### Definition

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SlowMethod {
    long threshold();  // Required — no default
}
```

### Attributes

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `threshold` | `long` | *(required)* | Threshold in milliseconds. Logs WARN only when exceeded. |

### Usage Examples

#### Alert only on slow execution

```java
@SlowMethod(threshold = 1000)
public void syncExternalInventory() {
    inventoryClient.fetchAndUpdate();
}
```

Output (only if execution > 1000ms):
```
WARN [DevLens][SLOW] class=InventoryService method=syncExternalInventory executionTime=2341ms threshold=1000ms
```

If it runs in 500ms — **no output at all**. Your logs stay clean.

### When to Use

- **Background jobs** — You don't care about normal runs, only want alerts when things degrade
- **Scheduled tasks** — Flag when a cron job takes longer than expected
- **Methods that are usually fast but occasionally spike** — Catch the outliers

### @ExecutionTime vs @SlowMethod

| Feature | @ExecutionTime | @SlowMethod |
|---------|---------------|-------------|
| Always logs time | Yes (at INFO) | No |
| Logs at WARN when slow | Yes (if threshold set) | Yes (always) |
| Threshold required | No (default 0) | Yes |
| Use case | "I want to see every execution time" | "Only tell me when it's slow" |

### Real-World Scenario: Detecting Database Connection Pool Exhaustion

```java
@SlowMethod(threshold = 100)
public Connection getConnection() {
    return dataSource.getConnection();
}
```

Getting a connection should be < 5ms. If it takes 100ms+, your pool is likely exhausted — threads are waiting for connections to be returned.

---

## @LogInput

**Purpose:** Logs the method's input arguments when it is called. Supports field-level masking for sensitive data.

### Definition

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogInput {
    String[] maskFields() default {};
}
```

### Attributes

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `maskFields` | `String[]` | `{}` | Field names to mask in the serialized output. Added on top of globally configured masked fields. |

### Usage Examples

#### Basic — Log all arguments

```java
@LogInput
public User findByEmail(String email) {
    return userRepository.findByEmail(email);
}
```

Output:
```
[DevLens] LogInput class=UserService method=findByEmail args=[email=john@example.com]
```

#### With masking — Hide sensitive fields

```java
@LogInput(maskFields = {"password", "ssn"})
public User register(RegistrationRequest request) {
    return userService.create(request);
}
```

Output:
```
[DevLens] LogInput class=AuthController method=register args=[RegistrationRequest{username=john, password=***, ssn=***, email=john@example.com}]
```

#### Multiple parameters

```java
@LogInput
public TransferResult transfer(String fromAccount, String toAccount, BigDecimal amount) {
    return bankingService.execute(fromAccount, toAccount, amount);
}
```

Output:
```
[DevLens] LogInput class=TransferService method=transfer args=[fromAccount=ACC-001, toAccount=ACC-002, amount=1500.00]
```

### When to Use

- **Debugging** — See exactly what arguments a method received
- **Audit logging** — Record what data entered the system
- **Reproducing bugs** — Log inputs to replay failing scenarios
- **API handlers** — Track incoming request payloads

### Real-World Scenario: Debugging Intermittent Failures

```java
@LogInput
@LogException
public Invoice calculateTax(InvoiceRequest request) {
    // Fails occasionally with NullPointerException — which field is null?
    return taxEngine.compute(request);
}
```

When it fails, you'll see the exact input that caused the NPE in your logs — no need to add temporary debug statements and redeploy.

### Masking Behavior

Masking happens AFTER serialization. The flow:
1. `ObjectSerializer.serialize(arg)` → produces a string like `{password=secret123, name=John}`
2. `SensitiveDataMasker.mask(string, maskFields)` → replaces values: `{password=***, name=John}`

Built-in auto-masked fields (always masked regardless of `maskFields`):
- `password`, `passwd`, `token`, `authorization`
- `accesstoken`, `refreshtoken`, `secret`, `cardnumber`, `cvv`

---

## @LogOutput

**Purpose:** Logs the method's return value after successful execution.

### Definition

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogOutput {
    int maxLength() default -1;
}
```

### Attributes

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `maxLength` | `int` | `-1` | Max characters to log. `-1` means use the global `devlens.max-output-length` (default 5000). |

### Usage Examples

#### Basic — Log the return value

```java
@LogOutput
public User findUser(Long id) {
    return userRepository.findById(id).orElse(null);
}
```

Output:
```
[DevLens] LogOutput class=UserService method=findUser result=User{id=42, name=John, role=ADMIN}
```

#### With length limit — Truncate large outputs

```java
@LogOutput(maxLength = 200)
public List<Product> searchProducts(String query) {
    return productRepository.search(query);
}
```

Output (truncated at 200 chars):
```
[DevLens] LogOutput class=ProductService method=searchProducts result=[Product{id=1, name=Widget}, Product{id=2, name=Gadget}, Pro...[truncated]
```

#### Not logged on exception

```java
@LogOutput
@LogException
public Data fetchData() {
    throw new RuntimeException("connection timeout");
}
```

`@LogOutput` produces nothing — it only logs on success. `@LogException` handles the failure case.

### When to Use

- **Verifying transformations** — Confirm a method produces expected output
- **Caching validation** — See what value is being cached
- **Data pipeline debugging** — Check intermediate transformation results
- **API response logging** — Record what your service returned

### Real-World Scenario: Validating Mapper Output

```java
@LogInput
@LogOutput
public OrderDTO mapToDTO(Order entity) {
    return modelMapper.map(entity, OrderDTO.class);
}
```

See both what went in and what came out — instantly spot mapping bugs (missing fields, wrong conversions).

---

## @LogException

**Purpose:** Logs detailed exception information when a method throws. Optionally includes the full stack trace.

### Definition

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogException {
    boolean includeStackTrace() default true;
}
```

### Attributes

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `includeStackTrace` | `boolean` | `true` | Whether to include the full stack trace in logs. |

### Usage Examples

#### Basic — Full exception logging with stack trace

```java
@LogException
public void processPayment(PaymentRequest request) {
    paymentGateway.charge(request);
}
```

Output:
```
ERROR [DevLens] Exception class=PaymentService method=processPayment exception=java.net.SocketTimeoutException message=Connect timed out executionTime=5002ms thread=http-nio-8080-exec-3
java.net.SocketTimeoutException: Connect timed out
    at java.net.Socket.connect(Socket.java:601)
    at com.example.PaymentGateway.charge(PaymentGateway.java:45)
    ...
```

#### Without stack trace — Compact error logging

```java
@LogException(includeStackTrace = false)
public Optional<User> findUser(String email) {
    return userRepository.findByEmail(email);
}
```

Output:
```
ERROR [DevLens] Exception class=UserService method=findUser exception=org.springframework.dao.DataAccessException message=could not execute query executionTime=120ms thread=http-nio-8080-exec-7
```

### When to Use

- **All service-layer methods** — Catch and log exceptions with context
- **Integration points** — Log failures from external calls with timing info
- **Critical business logic** — Ensure failures are always recorded
- **High-volume methods** — Use `includeStackTrace = false` to avoid log bloat

### What Gets Logged

Each exception log entry includes:
- Class name and method name
- Exception type (fully qualified class name)
- Exception message
- Execution time (how long the method ran before failing)
- Thread name
- Correlation ID (if `@CorrelationId` is also present)
- Stack trace (optional)

### Real-World Scenario: Monitoring External Service Failures

```java
@LogException
@ExternalCall("inventory-service")
@Retry(maxAttempts = 3)
public InventoryResponse checkStock(String sku) {
    return inventoryClient.get("/stock/" + sku);
}
```

When the external service fails, you get:
1. Retry attempt logs (from `@Retry`)
2. External call failure log (from `@ExternalCall`)
3. Detailed exception info with stack trace (from `@LogException`)

All correlated together if `@CorrelationId` is present on the caller.

---

## @Retry

**Purpose:** Automatically retries a method when it throws an exception. Supports configurable attempts, delay, exponential backoff, and exception filtering.

### Definition

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Retry {
    int maxAttempts() default 3;
    long delay() default 1000;
    boolean exponentialBackoff() default false;
    Class<? extends Throwable>[] retryOn() default {};
    Class<? extends Throwable>[] noRetryOn() default {};
}
```

### Attributes

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `maxAttempts` | `int` | `3` | Total number of attempts (including the first call). |
| `delay` | `long` | `1000` | Base delay between retries in milliseconds. |
| `exponentialBackoff` | `boolean` | `false` | If `true`, delay doubles on each retry: `delay * 2^(attempt-1)`. Capped at 30 seconds. |
| `retryOn` | `Class<? extends Throwable>[]` | `{}` | If specified, ONLY retry on these exception types. Empty = retry all (except `InterruptedException`). |
| `noRetryOn` | `Class<? extends Throwable>[]` | `{}` | NEVER retry on these exception types — checked first (takes precedence over `retryOn`). |

### Usage Examples

#### Basic — Retry 3 times with 1-second delay

```java
@Retry
public String fetchConfigFromRemote() {
    return httpClient.get("https://config.example.com/app.json");
}
```

Behavior:
```
Attempt 1: FAILED (SocketTimeoutException) → wait 1000ms
Attempt 2: FAILED (SocketTimeoutException) → wait 1000ms
Attempt 3: SUCCESS → return result
```

Output:
```
WARN [DevLens] Retry method=fetchConfigFromRemote attempt=1/3 status=FAILED exception=java.net.SocketTimeoutException
WARN [DevLens] Retry method=fetchConfigFromRemote attempt=2/3 status=FAILED exception=java.net.SocketTimeoutException
INFO [DevLens] Retry method=fetchConfigFromRemote attempt=3/3 status=SUCCESS
```

#### With exponential backoff

```java
@Retry(maxAttempts = 5, delay = 200, exponentialBackoff = true)
public void publishEvent(Event event) {
    messageBroker.send(event);
}
```

Retry delays:
```
Attempt 1: fail → wait 200ms   (200 * 2^0)
Attempt 2: fail → wait 400ms   (200 * 2^1)
Attempt 3: fail → wait 800ms   (200 * 2^2)
Attempt 4: fail → wait 1600ms  (200 * 2^3)
Attempt 5: fail → throw exception (EXHAUSTED)
```

Maximum delay is capped at 30,000ms regardless of calculation.

#### Retry only on specific exceptions

```java
@Retry(maxAttempts = 3, retryOn = {SocketTimeoutException.class, ConnectException.class})
public ApiResponse callExternalApi(String endpoint) {
    return restTemplate.getForObject(endpoint, ApiResponse.class);
}
```

Only retries on network-related exceptions. A `400 Bad Request` (`HttpClientErrorException`) is NOT retried — it's thrown immediately.

#### Never retry on specific exceptions

```java
@Retry(maxAttempts = 4, noRetryOn = {AuthenticationException.class, ValidationException.class})
public Order submitOrder(OrderRequest request) {
    return orderGateway.submit(request);
}
```

Retries everything EXCEPT authentication and validation failures (those will always fail regardless of retries).

### Exception Filtering Logic

```
1. Check noRetryOn first — if exception matches, NEVER retry (throw immediately)
2. If retryOn is empty — retry ALL exceptions (except InterruptedException)
3. If retryOn is specified — ONLY retry if exception matches one of the classes
```

### When to Use

- **External HTTP calls** — Network timeouts, connection resets
- **Message queue publishing** — Broker temporarily unavailable
- **Database operations** — Deadlock retries, transient connection failures
- **Distributed locks** — Lock acquisition failures

### When NOT to Use

- **Validation errors** — Bad input won't become good on retry
- **Authentication failures** — Invalid credentials won't fix themselves
- **Business logic failures** — "Insufficient funds" won't change without external action
- **Non-idempotent operations without protection** — Don't retry `transferMoney()` unless it has idempotency keys

### Real-World Scenario: Resilient Microservice Communication

```java
@Retry(maxAttempts = 3, delay = 500, exponentialBackoff = true,
       retryOn = {SocketTimeoutException.class, ServiceUnavailableException.class},
       noRetryOn = {BadRequestException.class})
@ExternalCall("user-service")
@LogException
public UserProfile getUserProfile(String userId) {
    return userServiceClient.getProfile(userId);
}
```

This handles transient failures (timeouts, 503s) with backoff, immediately fails on client errors (400s), and logs the exception with external call context.

---

## @CorrelationId

**Purpose:** Generates and propagates a unique correlation ID across all DevLens log entries for the annotated method (and nested calls on the same thread).

### Definition

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CorrelationId {
    // No attributes
}
```

### How It Works Internally

- Generates an 8-character hex ID (first 8 chars of UUID, e.g., `a3f2b91c`)
- Stored in a `ThreadLocal` — available for the entire call stack on that thread
- Also set in SLF4J `MDC` under key `devlens.correlationId` — appears in your log patterns automatically
- **Depth-aware**: Nested `@CorrelationId` methods reuse the same ID (doesn't generate a new one until the outermost call exits)

### Usage Examples

#### Basic — Correlate logs for a request

```java
@CorrelationId
@LogInput
@LogOutput
@ExecutionTime
public Order processOrder(OrderRequest request) {
    validateOrder(request);
    chargePayment(request);
    return createOrder(request);
}
```

Output (all lines share the same correlation ID):
```
[DevLens] LogInput class=OrderService method=processOrder correlationId=a3f2b91c args=[OrderRequest{...}]
[DevLens] ExecutionTime class=OrderService method=processOrder correlationId=a3f2b91c executionTime=342ms
[DevLens] LogOutput class=OrderService method=processOrder correlationId=a3f2b91c result=Order{id=123}
```

#### Nested calls — Same correlation ID

```java
@CorrelationId
public void handleRequest(Request req) {
    stepOne(req);  // If stepOne also has @CorrelationId, it reuses the parent's ID
    stepTwo(req);
}

@CorrelationId
@ExecutionTime
public void stepOne(Request req) {
    // correlationId is still the same as handleRequest's
}
```

Output:
```
[DevLens] ExecutionTime class=Service method=stepOne correlationId=a3f2b91c executionTime=50ms
```

Same `a3f2b91c` — because depth tracking prevents generating a new ID for nested calls.

#### With MDC — Appears in your log pattern

If your `logback.xml` includes `%X{devlens.correlationId}`:

```xml
<pattern>%d{HH:mm:ss} [%X{devlens.correlationId}] %-5level %logger - %msg%n</pattern>
```

ALL log statements (not just DevLens ones) within the method will show the correlation ID:
```
14:23:01 [a3f2b91c] INFO  c.e.OrderService - Processing order for customer 42
14:23:01 [a3f2b91c] DEBUG c.e.PaymentGateway - Charging card ending in 4242
14:23:02 [a3f2b91c] INFO  c.e.OrderService - Order created: ORD-123
```

### When to Use

- **API controllers** — Tie all processing for one HTTP request together
- **Event handlers** — Correlate all steps of processing a single event
- **Service entry points** — Any method that kicks off a multi-step operation
- **Debugging distributed flows** — When combined with request tracing

### Real-World Scenario: Tracing a User Action Through Multiple Services

```java
@CorrelationId
@ExecutionTime
@LogInput
public CheckoutResult checkout(Cart cart) {
    InventoryResult inv = reserveInventory(cart);    // correlationId in logs
    PaymentResult pay = chargeCustomer(cart);         // correlationId in logs
    ShipmentResult ship = scheduleShipment(cart);     // correlationId in logs
    return new CheckoutResult(inv, pay, ship);
}
```

When debugging "why did checkout fail for user X?", grep for the correlation ID and get every log line for that specific checkout — across all internal method calls.

---

## @ExternalCall

**Purpose:** Logs metadata about external service calls — service name, response time, success/failure status. Designed for tracking dependencies on other systems.

### Definition

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExternalCall {
    String value();           // Service name (required)
    long threshold() default 0;  // Slow-call threshold in ms
}
```

### Attributes

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `value` | `String` | *(required)* | Name of the external service being called. |
| `threshold` | `long` | `0` | If execution exceeds this (ms), logs at WARN. `0` = no threshold warning. |

### Usage Examples

#### Basic — Track an external API call

```java
@ExternalCall("payment-gateway")
public PaymentResult charge(CardInfo card) {
    return stripeClient.createCharge(card);
}
```

Output (success):
```
[DevLens] ExternalCall service=payment-gateway method=charge executionTime=234ms status=SUCCESS
```

Output (failure):
```
ERROR [DevLens] ExternalCall service=payment-gateway method=charge executionTime=5003ms status=FAILED exception=java.net.SocketTimeoutException
```

#### With threshold — Warn on slow responses

```java
@ExternalCall(value = "inventory-service", threshold = 500)
public StockLevel checkStock(String sku) {
    return inventoryClient.getStock(sku);
}
```

Output (fast — INFO):
```
[DevLens] ExternalCall service=inventory-service method=checkStock executionTime=89ms status=SUCCESS
```

Output (slow — WARN):
```
WARN [DevLens] ExternalCall service=inventory-service method=checkStock executionTime=1200ms status=SUCCESS
```

Note: even though it succeeded, it's logged at WARN because it exceeded the threshold.

#### Database as external call

```java
@ExternalCall(value = "postgres-primary", threshold = 100)
public List<Order> findRecentOrders(Long customerId) {
    return jdbcTemplate.query(SQL, customerId);
}
```

### When to Use

- **HTTP client calls** — REST APIs, SOAP services, GraphQL endpoints
- **Database queries** — Treat the DB as an external dependency
- **Message broker interactions** — Kafka produce, RabbitMQ publish
- **Cache operations** — Redis, Memcached calls
- **Third-party SDKs** — AWS SDK calls, payment providers, email services

### What Gets Logged

| Field | Description |
|-------|-------------|
| `service` | The service name from `value()` |
| `method` | The annotated method name |
| `executionTime` | Total call duration in ms |
| `status` | `SUCCESS` or `FAILED` |
| `exception` | Exception class (only if FAILED) |
| `correlationId` | If `@CorrelationId` is present |

### Real-World Scenario: Dependency Health Dashboard

```java
@ExternalCall(value = "recommendation-engine", threshold = 200)
public List<Product> getRecommendations(String userId) { ... }

@ExternalCall(value = "search-service", threshold = 150)
public SearchResult search(String query) { ... }

@ExternalCall(value = "user-profile-service", threshold = 100)
public UserProfile getProfile(String userId) { ... }
```

By parsing logs for `[DevLens] ExternalCall service=...`, you can build a dashboard showing:
- Average response time per dependency
- Failure rate per dependency
- Which dependency is causing the most timeouts

---

## @MemoryUsage

**Purpose:** Logs JVM heap memory usage before and after method execution, showing the memory delta.

### Definition

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MemoryUsage {
    boolean forceGC() default false;
}
```

### Attributes

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `forceGC` | `boolean` | `false` | If `true`, calls `System.gc()` before measuring memory. Provides more accurate baseline but adds latency. |

### How It Works

```
1. (Optional) System.gc() if forceGC = true
2. Capture usedHeapBytes BEFORE = Runtime.totalMemory() - Runtime.freeMemory()
3. Execute the method
4. Capture usedHeapBytes AFTER
5. Log: before, after, delta (after - before)
```

### Usage Examples

#### Basic — Track memory allocation

```java
@MemoryUsage
public List<Report> generateAllReports() {
    return reportEngine.buildAll();
}
```

Output:
```
[DevLens] MemoryUsage class=ReportService method=generateAllReports before=52428800B after=157286400B delta=+104857600B
```

(That's ~100MB allocated during report generation.)

#### With forced GC — More accurate baseline

```java
@MemoryUsage(forceGC = true)
public void loadLargeDataset(String path) {
    dataset = fileReader.readAll(path);
}
```

Output:
```
[DevLens] MemoryUsage class=DataLoader method=loadLargeDataset before=31457280B after=524288000B delta=+492830720B
```

`forceGC = true` triggers garbage collection before measuring, so `before` is a cleaner baseline (no leftover garbage from previous operations).

### When to Use

- **Large data processing** — Batch imports, report generation, file parsing
- **Cache loading** — Measure how much memory a cache population takes
- **Memory leak investigation** — If delta keeps growing on repeated calls, you may have a leak
- **Capacity planning** — Know how much heap a specific operation requires

### When NOT to Use

- **High-frequency methods** — Memory measurement adds slight overhead; don't put this on methods called 1000x/second
- **Production hot paths** — Use in dev/staging for profiling, not in production under load
- **With `forceGC = true` in production** — `System.gc()` causes stop-the-world pauses

### Real-World Scenario: Detecting Memory-Heavy Operations

```java
@MemoryUsage
@ExecutionTime
@SlowMethod(threshold = 5000)
public void importCsvFile(MultipartFile file) {
    List<Record> records = csvParser.parse(file.getInputStream());
    batchInsert(records);
}
```

If a 50MB CSV causes `delta=+800MB`, you know the parser is creating too many intermediate objects. Time to switch to streaming.

---

## @ThreadInfo

**Purpose:** Logs the current thread's name, ID, and state at method entry, exit, or both. Essential for debugging concurrency issues.

### Definition

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ThreadInfo {
    LogAt logAt() default LogAt.START;
}
```

### Attributes

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `logAt` | `LogAt` | `START` | When to log: `START` (entry only), `END` (exit only), `BOTH` (entry and exit). |

### The `LogAt` Enum

```java
public enum LogAt {
    START,  // Log at method entry
    END,    // Log at method exit
    BOTH    // Log at both entry and exit
}
```

### What Gets Logged

| Field | Description |
|-------|-------------|
| `thread` | Thread name (e.g., `http-nio-8080-exec-3`, `pool-1-thread-7`) |
| `threadId` | JVM thread ID (unique long) |
| `state` | Thread state: `RUNNABLE`, `WAITING`, `BLOCKED`, `TIMED_WAITING` |
| `phase` | `ENTRY` or `EXIT` |

### Usage Examples

#### Basic — Log thread at entry

```java
@ThreadInfo
public void handleWebhook(WebhookPayload payload) {
    webhookProcessor.process(payload);
}
```

Output:
```
[DevLens] ThreadInfo class=WebhookController method=handleWebhook thread=http-nio-8080-exec-5 threadId=38 state=RUNNABLE phase=ENTRY
```

#### Log at both entry and exit

```java
@ThreadInfo(logAt = LogAt.BOTH)
public CompletableFuture<Report> generateReportAsync(String userId) {
    return CompletableFuture.supplyAsync(() -> reportBuilder.build(userId));
}
```

Output:
```
[DevLens] ThreadInfo class=ReportService method=generateReportAsync thread=http-nio-8080-exec-2 threadId=31 state=RUNNABLE phase=ENTRY
[DevLens] ThreadInfo class=ReportService method=generateReportAsync thread=http-nio-8080-exec-2 threadId=31 state=RUNNABLE phase=EXIT
```

#### Log only at exit

```java
@ThreadInfo(logAt = LogAt.END)
public void cleanup() {
    resourcePool.releaseAll();
}
```

Output:
```
[DevLens] ThreadInfo class=ResourceManager method=cleanup thread=scheduler-1 threadId=55 state=RUNNABLE phase=EXIT
```

### When to Use

- **Async methods** — Verify work is dispatched to the correct thread pool
- **Scheduled tasks** — Confirm the scheduler thread is handling the job
- **Thread pool starvation** — Detect when thread IDs climb toward pool max
- **Deadlock investigation** — Check if threads are BLOCKED
- **Reactive/async handoff** — Detect thread switches (entry thread != exit thread)

### Real-World Scenarios

#### Detecting Thread Switches (Async)

```java
@Async("emailPool")
@ThreadInfo(logAt = LogAt.BOTH)
public void sendEmail(String to, String body) {
    emailClient.send(to, body);
}
```

Output:
```
[DevLens] ThreadInfo class=EmailService method=sendEmail thread=emailPool-1-thread-3 threadId=62 state=RUNNABLE phase=ENTRY
[DevLens] ThreadInfo class=EmailService method=sendEmail thread=emailPool-1-thread-3 threadId=62 state=RUNNABLE phase=EXIT
```

If you see `thread=http-nio-8080-exec-X` instead of `emailPool-*`, your `@Async` isn't working (common issue: self-invocation bypasses the proxy).

#### Thread Pool Exhaustion

```java
@ThreadInfo(logAt = LogAt.BOTH)
@ExternalCall("payment-gateway")
public PaymentResult chargeCard(CardInfo card) {
    return httpClient.post("/charge", card);  // blocking call
}
```

If you see thread IDs climbing (exec-198, exec-199, exec-200) and the pool max is 200, your blocking calls are consuming all threads.

#### Wrong Thread Pool

```java
@ThreadInfo
public CompletableFuture<Data> loadData(String key) {
    return CompletableFuture.supplyAsync(() -> db.query(key));
}
```

Output:
```
[DevLens] ThreadInfo class=DataService method=loadData thread=ForkJoinPool.commonPool-worker-3 threadId=19 state=RUNNABLE phase=ENTRY
```

A blocking DB call on `ForkJoinPool.commonPool` is a problem — it starves parallel streams and other CompletableFutures. Should use a dedicated IO pool.

---

## @DevTrace

**Purpose:** All-in-one comprehensive tracing. Produces a single structured log record containing input, output, timing, thread info, correlation, and status. Replaces the need for combining multiple individual annotations.

### Definition

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DevTrace {
    long threshold() default 0;
    String[] maskFields() default {};
    int maxOutputLength() default -1;
    boolean includeStackTrace() default true;
}
```

### Attributes

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `threshold` | `long` | `0` | If set and execution exceeds this (ms), additionally triggers WARN-level execution time log. |
| `maskFields` | `String[]` | `{}` | Fields to mask in input/output serialization. |
| `maxOutputLength` | `int` | `-1` | Max chars for output. `-1` = global config default (5000). |
| `includeStackTrace` | `boolean` | `true` | Include stack trace in the trace record on exception. |

### Output Format

```
[DevLens] ═══════ DevTrace ═══════
 class     : OrderService
 method    : createOrder
 thread    : http-nio-8080-exec-4 (id=33)
 start     : 14:23:01.234
 end       : 14:23:01.567
 elapsed   : 333ms
 status    : SUCCESS
 correlationId : a3f2b91c
 input     : [OrderRequest{customerId=42, items=3, cardNumber=***}]
 output    : Order{id=ORD-789, status=CREATED}
[DevLens] ═══════════════════════
```

### Usage Examples

#### Basic — Full trace of a method

```java
@DevTrace
public Order createOrder(OrderRequest request) {
    return orderRepository.save(request.toEntity());
}
```

#### With masking and output length

```java
@DevTrace(maskFields = {"cardNumber", "cvv"}, maxOutputLength = 500)
public PaymentResult processPayment(PaymentRequest request) {
    return paymentGateway.execute(request);
}
```

#### On failure

```java
@DevTrace(includeStackTrace = true)
public User authenticate(String username, String password) {
    return authService.login(username, password);
}
```

Output on failure:
```
[DevLens] ═══════ DevTrace ═══════
 class     : AuthController
 method    : authenticate
 thread    : http-nio-8080-exec-1 (id=28)
 start     : 14:25:00.100
 end       : 14:25:00.230
 elapsed   : 130ms
 status    : FAILED
 input     : [username=john, password=***]
 exception : AuthenticationException: Invalid credentials
[DevLens] ═══════════════════════
```

### @DevTrace vs Individual Annotations

| What you get | @DevTrace | Individual annotations needed |
|---|---|---|
| Input logging | Included | `@LogInput` |
| Output logging | Included | `@LogOutput` |
| Execution time | Included | `@ExecutionTime` |
| Thread info | Included | `@ThreadInfo` |
| Exception info | Included | `@LogException` |
| Correlation | Included (if @CorrelationId also present) | `@CorrelationId` |
| Single structured record | Yes | No (separate log lines) |

### When to Use

- **Critical business methods** — One annotation gives you complete observability
- **API entry points** — Full request/response tracing in one log block
- **Debugging sessions** — Temporarily add to a method to get full context
- **Methods you always want full visibility into**

### When to Use Individual Annotations Instead

- You only need execution time (don't want input/output noise)
- High-frequency methods where full tracing is too verbose
- You want different configurations per concern (e.g., only mask on input, only WARN on slow)

### Combining @DevTrace with Other Annotations

`@DevTrace` can coexist with `@SlowMethod`, `@ExternalCall`, `@MemoryUsage`, and `@Retry`:

```java
@DevTrace(maskFields = {"password"})
@SlowMethod(threshold = 1000)
@ExternalCall("auth-service")
@Retry(maxAttempts = 2)
@CorrelationId
public LoginResult login(Credentials credentials) {
    return authClient.authenticate(credentials);
}
```

This produces:
1. The DevTrace structured block (input, output, timing, thread, etc.)
2. A WARN if execution > 1000ms (from `@SlowMethod`)
3. External call logging with service name (from `@ExternalCall`)
4. Retry attempt logs if it fails and retries (from `@Retry`)
5. All correlated with the same ID (from `@CorrelationId`)

---

## Combining Annotations

Annotations can be freely combined. The engine processes them in a defined order.

### Processing Order

```
1. @CorrelationId    → Generate/push ID (pre)
2. @MemoryUsage      → Capture heap before (pre)
3. @LogInput         → Serialize and log args (pre)
4. @ThreadInfo       → Log thread at ENTRY (pre, if START or BOTH)
5. @Retry            → Wrap execution in retry loop
6. [method executes]
7. @ExecutionTime    → Log elapsed time (post)
8. @SlowMethod       → Warn if threshold exceeded (post)
9. @ExternalCall     → Log service call metadata (post)
10. @LogOutput       → Serialize and log return value (post)
11. @LogException    → Log exception details (post)
12. @ThreadInfo      → Log thread at EXIT (post, if END or BOTH)
13. @MemoryUsage     → Capture heap after, log delta (post)
14. @CorrelationId   → Pop/cleanup (post)
```

### Common Combinations

#### Full observability for a service method

```java
@CorrelationId
@LogInput(maskFields = {"password"})
@LogOutput
@LogException
@ExecutionTime(threshold = 500)
public AuthResult login(String username, String password) {
    return authService.authenticate(username, password);
}
```

#### Resilient external call with monitoring

```java
@CorrelationId
@ExternalCall(value = "order-service", threshold = 300)
@Retry(maxAttempts = 3, delay = 500, exponentialBackoff = true,
       retryOn = {SocketTimeoutException.class})
@LogException
@ExecutionTime
public OrderStatus getOrderStatus(String orderId) {
    return orderClient.getStatus(orderId);
}
```

#### Memory-intensive batch operation

```java
@CorrelationId
@MemoryUsage(forceGC = true)
@ExecutionTime
@SlowMethod(threshold = 30000)
@LogException
public void importDataFile(Path filePath) {
    dataImporter.process(filePath);
}
```

#### Async worker with thread tracking

```java
@Async("workerPool")
@ThreadInfo(logAt = LogAt.BOTH)
@ExecutionTime
@LogException
@CorrelationId
public void processMessage(Message message) {
    messageHandler.handle(message);
}
```

---

## Configuration Reference

All configuration is via `application.properties` or `application.yml` under the `devlens.*` prefix.

### Complete Properties List

```yaml
devlens:
  # Global kill switch — disables ALL DevLens processing
  enabled: true

  # Maximum characters for serialized output (used by @LogOutput and @DevTrace)
  max-output-length: 5000

  # Per-feature toggles — disable specific annotations without removing them from code
  execution-time:
    enabled: true
    threshold: 0          # Global threshold override (ms)

  log-input:
    enabled: true

  log-output:
    enabled: true

  log-exception:
    enabled: true

  thread-info:
    enabled: true

  correlation-id:
    enabled: true

  external-call:
    enabled: true

  retry:
    enabled: true

  memory-usage:
    enabled: true

  # Data masking — fields that are always masked in ALL serialized output
  masking:
    fields:
      - password
      - ssn
      - creditCardNumber
      - apiKey
```

### Complete Properties File (application.properties format)

```properties
# ═══════════════════════════════════════════════════
# DevLens Configuration — All Properties (with defaults)
# ═══════════════════════════════════════════════════

# Global kill switch (disables ALL DevLens processing)
devlens.enabled=true

# Maximum characters for serialized output (@LogOutput, @DevTrace)
devlens.max-output-length=5000

# ─── Feature Toggles ───────────────────────────────

# @ExecutionTime and @SlowMethod
devlens.execution-time.enabled=true
devlens.execution-time.threshold=0

# @LogInput
devlens.log-input.enabled=true

# @LogOutput
devlens.log-output.enabled=true

# @LogException
devlens.log-exception.enabled=true

# @ThreadInfo
devlens.thread-info.enabled=true

# @CorrelationId
devlens.correlation-id.enabled=true

# @ExternalCall
devlens.external-call.enabled=true

# @Retry
devlens.retry.enabled=true

# @MemoryUsage
devlens.memory-usage.enabled=true

# ─── Data Masking ──────────────────────────────────

# Additional fields to always mask (comma-separated)
# Built-in auto-masked: password, passwd, token, authorization,
#   accesstoken, refreshtoken, secret, cardnumber, cvv
devlens.masking.fields=
```

> **Note:** If you don't declare any `devlens.*` property in your file, all features default to **enabled**. You only need to add properties when you want to change something from the default.

### Key Configuration Patterns

#### Development — Full verbose logging

```yaml
devlens:
  enabled: true
  max-output-length: 10000
```

#### Production — Minimal overhead

```yaml
devlens:
  enabled: true
  log-input:
    enabled: false        # Don't log inputs in prod (performance + PII)
  log-output:
    enabled: false        # Don't log outputs in prod
  memory-usage:
    enabled: false        # Disable memory tracking overhead
  execution-time:
    threshold: 500        # Only log if slow
  masking:
    fields:
      - password
      - ssn
      - token
      - cardNumber
```

#### Disable completely (e.g., for load tests)

```yaml
devlens:
  enabled: false
```

When `enabled = false`, the engine immediately calls `proceed()` — near-zero overhead (just one `if` check).

---

## Customization

### Custom Serialization

The `ObjectSerializer` interface controls how objects are converted to strings for logging.

```java
public interface ObjectSerializer {
    String serialize(Object object);
}
```

Default: `ToStringSerializer` — calls `object.toString()`.

#### Provide a Jackson-based serializer

```java
@Bean
public ObjectSerializer devLensObjectSerializer() {
    ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    return object -> {
        if (object == null) return "null";
        try {
            return mapper.writeValueAsString(object);
        } catch (Exception e) {
            return object.toString();
        }
    };
}
```

Now all `@LogInput`, `@LogOutput`, and `@DevTrace` output will be JSON:
```
[DevLens] LogInput class=OrderService method=create args=[{"customerId":42,"items":[{"sku":"A1","qty":2}]}]
```

#### Provide a Gson-based serializer

```java
@Bean
public ObjectSerializer devLensObjectSerializer() {
    Gson gson = new GsonBuilder().serializeNulls().create();
    return object -> object == null ? "null" : gson.toJson(object);
}
```

### Custom Masking

The `SensitiveDataMasker` interface controls how sensitive fields are redacted.

```java
public interface SensitiveDataMasker {
    String mask(String serialized, String... additionalFields);
}
```

Default: `DefaultSensitiveDataMasker` — replaces values with `***` using regex pattern matching.

#### Provide a custom masker with partial masking

```java
@Bean
public SensitiveDataMasker devLensSensitiveDataMasker(DevLensProperties properties) {
    return (serialized, additionalFields) -> {
        String result = serialized;

        // Partial mask credit cards: ****-****-****-1234
        result = result.replaceAll(
            "\"(cardNumber|creditCard)\"\\s*:\\s*\"(\\d{12})(\\d{4})\"",
            "\"$1\":\"****-****-****-$3\""
        );

        // Mask emails: j***@example.com
        result = result.replaceAll(
            "([a-zA-Z])[a-zA-Z.]+@",
            "$1***@"
        );

        // Standard field masking
        Set<String> fieldsToMask = new HashSet<>(properties.getMasking().getFields());
        if (additionalFields != null) {
            fieldsToMask.addAll(Arrays.asList(additionalFields));
        }
        for (String field : fieldsToMask) {
            result = result.replaceAll(
                "(?i)(\"?" + Pattern.quote(field) + "\"?\\s*[:=]\\s*)\"[^\"]*\"",
                "$1\"***\""
            );
        }

        return result;
    };
}
```

Output:
```
[DevLens] LogInput class=PaymentService method=charge args=[{cardNumber=****-****-****-4242, email=j***@example.com, amount=99.99}]
```

### Extension Points Summary

| Interface | Default | Purpose | Override by |
|-----------|---------|---------|------------|
| `ObjectSerializer` | `ToStringSerializer` | Object → String conversion | Define `@Bean ObjectSerializer` |
| `SensitiveDataMasker` | `DefaultSensitiveDataMasker` | Redact sensitive values | Define `@Bean SensitiveDataMasker` |

Both use `@ConditionalOnMissingBean` — your bean automatically replaces the default.

---

## Architecture Overview

### Module Dependency

```
┌─────────────────────────┐
│  devlens-annotations    │  ← Pure @interface definitions, zero deps
└────────────┬────────────┘
             │ depends on
┌────────────▼────────────┐
│  devlens-core           │  ← Engine + services, needs only annotations + slf4j
│  ├─ InterceptorEngine   │
│  ├─ InterceptionContext │
│  ├─ TimingService       │
│  ├─ LoggingService      │
│  ├─ RetryService        │
│  ├─ CorrelationService  │
│  ├─ MemoryService       │
│  ├─ ObjectSerializer    │
│  └─ SensitiveDataMasker │
└────────────┬────────────┘
             │ depends on
┌────────────▼────────────┐
│  devlens-spring-boot-   │  ← Spring AOP glue + auto-configuration
│  starter                │
│  ├─ DevLensAspect       │
│  ├─ AutoConfiguration   │
│  └─ DevLensProperties   │
└─────────────────────────┘
```

### Runtime Flow

```
Method call with DevLens annotation
        │
        ▼
┌─────────────────┐
│  DevLensAspect  │  Spring AOP @Around advice intercepts the call
│  (or CDI/proxy) │
└────────┬────────┘
         │ builds
         ▼
┌─────────────────────┐
│ InterceptionContext  │  Framework-agnostic context object
│ • Method            │
│ • Arguments         │
│ • Annotations       │
│ • ProceedFunction   │
└────────┬────────────┘
         │ passed to
         ▼
┌──────────────────────────┐
│ DevLensInterceptorEngine │  Central orchestrator
│                          │
│  config.isEnabled()?─────┼──No──→ proceed() immediately
│         │ Yes            │
│         ▼                │
│  @DevTrace present?──────┼──Yes──→ handleDevTrace()
│         │ No             │
│         ▼                │
│  handleIndividualAnnot() │
│  PRE → EXECUTE → POST   │
└──────────────────────────┘
         │
         ▼
    Return result (or re-throw exception)
```

### Error Isolation

Every annotation processing step is independently try-caught:

```java
try {
    if (config.isLogInputEnabled() && context.hasAnnotation(LogInput.class)) {
        logInput(context, correlationId);
    }
} catch (Exception e) {
    loggingService.logInternalError("log-input", e);
    // continues to next step — your method is NEVER affected
}
```

If DevLens internals fail (serialization error, masking regex issue, etc.), the error is logged as a warning and processing continues. Your application method always behaves exactly as if DevLens wasn't there.

---

## Quick Reference Card

| Annotation | Purpose | Key Attribute |
|------------|---------|---------------|
| `@ExecutionTime` | Log method duration | `threshold` (ms) |
| `@SlowMethod` | Warn only when slow | `threshold` (ms, required) |
| `@LogInput` | Log method arguments | `maskFields` |
| `@LogOutput` | Log return value | `maxLength` |
| `@LogException` | Log exception details | `includeStackTrace` |
| `@Retry` | Auto-retry on failure | `maxAttempts`, `delay`, `exponentialBackoff`, `retryOn`, `noRetryOn` |
| `@CorrelationId` | Link logs with unique ID | *(none)* |
| `@ExternalCall` | Track external dependencies | `value` (service name), `threshold` |
| `@MemoryUsage` | Track heap allocation | `forceGC` |
| `@ThreadInfo` | Log thread metadata | `logAt` (START/END/BOTH) |
| `@DevTrace` | All-in-one structured trace | `threshold`, `maskFields`, `maxOutputLength`, `includeStackTrace` |
