# Architecture & Code Style Guide: PDHD

## Purpose

This document defines the standard coding guidelines, formatting rules, and best practices for all human-written code and technical documentation within the PDHD repository. Consistency is paramount for code readability and maintenance, especially when onboarding new team members or reviewing code years later.

## General Guidelines

1. **Indentation:** Use 4 spaces. Never use tabs.
2. **Line Length:** Max 100 characters per line.
3. **Naming Conventions:**
   - **Classes/Modules:** PascalCase (e.g., `UserQueryService`).
   - **Functions/Methods:** snake_case (e.g., `fetch_user_details`).
   - **Variables:** snake_case.
   - **Constants:** ALL_CAPS_WITH_UNDERSCORES.
4. **Comments:** Use docstrings (preferably Google or NumPy style) for all public methods and classes. Comments should explain _why_ the code is complex, not _what_ the code is doing.

## Technical Deep Dives

- **Error Handling:** Prefer custom exceptions (e.g., `UserNotFoundException`) over generic error codes.
- **Magic Strings:** Avoid them. Define all key constants (like state names, URLs, model placeholders) in a centralized `config.py` or `constants.py` file.

## 🚨 The "Anti-Pattern" Prohibition (Do NOT do this)

### 1. No "Tool Logic Sprawl"

`@Tool` classes (in `ac.uk.sussex.kn253.tools`) must remain **thin wrappers**.

- **PROHIBITED:** Implementing path resolution, filesystem I/O, or complex validation inside the `@Tool` method.
- **PROHIBinthibited:** Creating `private` helper methods within the `@Tool` class to manage complex logic. If a method requires a helper, the logic belongs in a `Service`.

### 2. No "Support" Junk

We reject the use of "Support," "Constants," or "Utils" classes that merely act as containers for magic strings or static values.

- **PROHIBITED:** Creating `ToolSupport.java` or `SchemaKeys.java` to store string constants.
- **MANDATORY:** Use `@ConfigProperty` from `application.properties` for environment/config values. For domain-specific keys, use the actual service or entity that owns the logic.

### 3. No Manual Telemetry Wrapping

- **PROHIBITED:** Manual `try-finally` blocks in every tool method to record telemetry. This creates massive "noise" and obscures the tool's intent.

---

## ✅ The "Service-First" Mandate (Do this)

### 1. Symmetric Tool/Service Architecture

Every `@Tool` is an entry point to a **Service**.

- **The Tool:** Handles the LangChain4j `@Tool` and `@P` annotations. It calls a single method on an injected service.
- **The Service:** An `@ApplicationScoped` bean that contains the actual implementation (e.g., `PathResolutionService.java`).

### 2. Logic belongs in `@ApplicationScoped` Services

All complex, reusable, or security-sensitive logic **MUST** reside in properly injected, `@ApplicationScoped` beans.

- **Filesystem Services:** `PathResolutionService`, `FileDiscoveryService`.
- **Security Services:** `SecurityBoundaryService` (handles all `SecurityException` logic).
- **Audit/Telemetry Services:** `AuditService` (handles all telemetry recording).

### 3. Standardized Error & Telemetry via Interceptors

To keep tools thin, use CDI Interceptors or a Base Service pattern to handle cross-cutting concerns:

- **Telemetry:** Use an interceptor to automatically record `duration`, `success/failure`, and `errorClass` for any method annotated with `@TrackTelemetry`.
- **Error Mapping:** Use an interceptor to transform `IOException` or `SecurityException` into user-facing tool strings.

### 4. Dependency Injection (DI) is King

Everything must be discoverable and injectable. If you find yourself manually instantiating a class, you are breaking the architecture.

---

## 🛠️ Summary Table for Developers

| Feature            | ❌ WRONG (The "Agent Drift")                     | ✅ RIGHT (The "Quarkus Way")                 |
| :----------------- | :----------------------------------------------- | :------------------------------------------- |
| **Logic Location** | `private` methods in `@Tool` class               | `@ApplicationScoped` Service                 |
| **Constants**      | `public static final String` in `Support.java`   | `@ConfigProperty` or Service-owned constants |
| **Security**       | `if (!path.startsWith(root)) { ... }` in `@Tool` | `SecurityService.validatePath(path)`         |
| **Telemetry**      | `try-finally` in every tool method               | `@TrackTelemetry` Interceptor                |
| **Error Handling** | Manual `try-catch` in every tool method          | Interceptor-based error translation          |
