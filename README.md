# DevLens

**Developer Utility Annotation Jar for Java 17+**

CodeLens simplifies common development tasks — execution time measurement, method tracing, input/output logging, exception tracking, retry handling, and more — through simple method-level annotations. Add the Spring Boot starter, annotate your methods, and you're done.

## Key Principles

- **Zero configuration** — auto-configures with Spring Boot, no manual bean setup
- **Fail-safe** — DevLens never breaks your application; all internal errors are caught and logged
- **Pluggable** — bring your own serializer or masking implementation
- **Toggleable** — enable/disable any feature per environment via properties
- **Non-invasive** — return values and exceptions pass through unchanged

## Quick Start

### 1. Add the dependency

```xml
<dependency>
    <groupId>com.devlens</groupId>
    <artifactId>devlens-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. Annotate your methods

```java
import com.devlens.annotation.*;

@Service
public class OrderService {

    @ExecutionTime
    @LogInput
    @LogOutput
    public Order createOrder(CreateOrderRequest request) {
        // your business logic
        return orderRepository.save(new Order(request));
    }
}
```

### 3. Run your application

DevLens auto-configures via Spring Boot. Logs appear via SLF4J:

```
[DevLens] LogInput class=OrderService method=createOrder args=[input=CreateOrderRequest{customerId=42, items=[...]}]
[DevLens] LogOutput class=OrderService method=createOrder result=Order{id=1001, status=CREATED}
[DevLens] ExecutionTime class=OrderService method=createOrder executionTime=23ms
```

## Annotations

### @ExecutionTime

Measures and logs method execution time.

```java
@ExecutionTime(threshold = 500) // logs at WARN if > 500ms, DEBUG otherwise
public void processData() { ... }
```

### @SlowMethod

Logs a warning only when execution exceeds a threshold. No output otherwise.

```java
@SlowMethod(threshold = 1000) // warns only if > 1000ms
public void heavyComputation() { ... }
```

### @LogInput

Logs method arguments at entry. Supports sensitive data masking.

```java
@LogInput(maskFields = {"creditCard", "ssn"})
public void registerUser(String name, String creditCard, String ssn) { ... }
```

Output:
```
[DevLens] LogInput class=UserService method=registerUser args=[name=John, creditCard=***, ssn=***]
```

### @LogOutput

Logs method return value. Supports output length truncation.

```java
@LogOutput(maxLength = 200)
public List<Product> search(String query) { ... }
```

### @LogException

Logs exceptions with full context without swallowing them.

```java
@LogException(includeStackTrace = true)
public void riskyOperation() { ... }
```

Output on exception:
```
[DevLens] Exception class=Service method=riskyOperation exception=IllegalStateException message=Something went wrong executionTime=12ms thread=main
```

### @ThreadInfo

Logs thread name, ID, and state at method entry/exit.

```java
@ThreadInfo(logAt = LogAt.BOTH) // START, END, or BOTH
public void concurrentTask() { ... }
```

### @CorrelationId

Generates and propagates a unique ID across nested method calls for log correlation.

```java
@CorrelationId
public void handleRequest(Request req) {
    // All DevLens logs within this call chain include the same correlationId
    processStep1();
    processStep2();
}
```

### @ExternalCall

Tracks external API calls with timing and status.

```java
@ExternalCall(value = "payment-gateway", threshold = 3000)
public PaymentResult chargeCard(PaymentRequest req) { ... }
```

Output:
```
[DevLens] ExternalCall service=payment-gateway method=chargeCard executionTime=1250ms status=SUCCESS
```

### @Retry

Automatic retry with configurable policies.

```java
@Retry(maxAttempts = 3, delay = 1000, exponentialBackoff = true,
       retryOn = {TimeoutException.class},
       noRetryOn = {ValidationException.class})
public Response callExternalApi() { ... }
```

### @MemoryUsage

Reports JVM heap memory before and after method execution.

```java
@MemoryUsage(forceGC = true)
public void loadLargeDataset() { ... }
```

Output:
```
[DevLens] MemoryUsage class=DataLoader method=loadLargeDataset before=52428800B after=157286400B delta=+104857600B
```

### @DevTrace

Combines ExecutionTime + LogInput + LogOutput + LogException + ThreadInfo into a single formatted block.

```java
@DevTrace(threshold = 500, maskFields = {"password"}, maxOutputLength = 1000)
public User authenticate(String username, String password) { ... }
```

Output:
```
[DevLens] ═══════ DevTrace ═══════
 class     : AuthService
 method    : authenticate
 thread    : http-nio-8080-exec-1 (id=42)
 start     : 2024-03-15T10:30:00.123Z
 end       : 2024-03-15T10:30:00.456Z
 elapsed   : 333ms
 status    : SUCCESS
 correlationId : a1b2c3d4
 input     : [username=admin, password=***]
 output    : User{id=1, role=ADMIN}
[DevLens] ═══════════════════════
```

## Configuration

All features are configurable via `application.properties` or `application.yml`:

```yaml
devlens:
  enabled: true                    # Master switch (default: true)
  max-output-length: 5000          # Max serialized output chars (default: 5000)
  
  execution-time:
    enabled: true                  # Toggle @ExecutionTime (default: true)
    threshold: 0                   # Global default threshold in ms (default: 0)
  
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
  
  masking:
    fields:                        # Additional fields to mask beyond defaults
      - apiKey
      - socialSecurityNumber
```

### Default Masked Fields

These fields are masked automatically (case-insensitive):
`password`, `passwd`, `token`, `authorization`, `accessToken`, `refreshToken`, `secret`, `cardNumber`, `cvv`

### Disabling DevLens per Environment

```properties
# application-prod.properties
devlens.enabled=false
```

When disabled, DevLens adds near-zero overhead — it short-circuits before any processing.

## Architecture

```
devlens-parent (pom)
├── devlens-annotations      → Zero dependencies, just @interface definitions
├── devlens-core             → Framework-agnostic engine (depends on SLF4J only)
└── devlens-spring-boot-starter → Auto-configuration for Spring Boot 3.x
```

### How It Works

1. **Spring AOP Aspect** (`DevLensAspect`) intercepts any method annotated with a DevLens annotation
2. **Interception Context** is built from the join point (method, args, annotations)
3. **DevLensInterceptorEngine** processes annotations in order:
   - Check master switch and per-feature toggles
   - Manage correlation ID (if @CorrelationId present)
   - Log input (if @LogInput present)
   - Log thread info at entry (if @ThreadInfo with START/BOTH)
   - **Execute the actual method**
   - Log output / handle exceptions / apply retry logic
   - Log execution time / slow method detection
   - Log thread info at exit
   - Log memory usage
   - Produce DevTrace block (if @DevTrace present)
4. **Return value or exception** passes through unchanged to the caller

### Fail-Safe Design

Every processing step is wrapped in try-catch:

```
try {
    preProcess(context);   // serialize inputs, log thread info, etc.
} catch (Exception e) {
    logInternalError(e);   // log at WARN, continue
}

result = proceed();        // ALWAYS called

try {
    postProcess(context);  // serialize output, log timing, etc.
} catch (Exception e) {
    logInternalError(e);   // log at WARN, don't interfere
}

return result;             // original result returned unchanged
```

## Custom Serialization

Provide your own `ObjectSerializer` bean to use Jackson, Gson, or any serialization library:

```java
@Configuration
public class DevLensConfig {

    @Bean
    public ObjectSerializer devLensSerializer(ObjectMapper mapper) {
        return object -> {
            try {
                return mapper.writeValueAsString(object);
            } catch (JsonProcessingException e) {
                return object.toString();
            }
        };
    }
}
```

## Custom Masking

Provide your own `SensitiveDataMasker` bean:

```java
@Bean
public SensitiveDataMasker customMasker() {
    return (serialized, additionalFields) -> {
        // your custom masking logic
        return masked;
    };
}
```

## Using Without Spring Boot

The `devlens-core` module has no Spring dependency. You can use it with plain Java:

```java
ObjectSerializer serializer = new ToStringSerializer();
SensitiveDataMasker masker = new DefaultSensitiveDataMasker();
DevLensConfiguration config = DevLensConfiguration.builder().build();

DevLensInterceptorEngine engine = new DevLensInterceptorEngine(serializer, masker, config);

// Build context and call engine.intercept(context) from your own proxy/interceptor
```

## Building

```bash
mvn clean install
```

Requires Java 17+.

## Testing

```bash
mvn test
```

The project includes 258 tests:
- **25 property-based correctness properties** using jqwik (100+ iterations each)
- Unit tests for all services and components
- Spring Boot integration tests for auto-configuration

## Log Level Reference

| Annotation | Normal | Threshold Exceeded | Exception |
|---|---|---|---|
| @ExecutionTime | DEBUG | WARN | — |
| @SlowMethod | (no output) | WARN | — |
| @LogInput | DEBUG | — | — |
| @LogOutput | DEBUG | — | — |
| @LogException | — | — | ERROR |
| @ThreadInfo | DEBUG | — | — |
| @ExternalCall | INFO | WARN | ERROR |
| @Retry (per attempt) | INFO (success) | — | WARN (fail) / ERROR (exhausted) |
| @MemoryUsage | DEBUG | — | — |
| @DevTrace | DEBUG | — | — |

