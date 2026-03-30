# Nullability Report — Tool-Calling Infrastructure

> Scope: `services/tools/`, `services/tools/macro/` and their sub-packages,
> `services/ToolService.java`.  
> Audit date: post-refactoring (after `MacroToolModule` consolidation).

---

## Legend

| Symbol | Meaning |
|---|---|
| ✅ | Null handled correctly |
| ⚠️ | Nullable by design but undocumented / no `@Nullable` annotation |
| ❌ | Missing null guard — latent NPE risk |

---

## 1. `ToolMacroDefinition` (record)

| Field | Nullable? | Handling | Status |
|---|---|---|---|
| `name` | No | `Objects.requireNonNull` in compact constructor | ✅ |
| `description` | No | `Objects.requireNonNull` | ✅ |
| `operationType` | No | `Objects.requireNonNull` | ✅ |
| `signals` | Yes (coerced) | `null` → `Map.of()` | ✅ |
| `invocationKeyphrases` | Yes (coerced) | `null` → `List.of()` | ✅ |

---

## 2. `ToolMacros` (static catalogue)

| Site | Nullable? | Handling | Status |
|---|---|---|---|
| `canonicalName(rawName, ...)` — `rawName` param | Yes | `null`/blank check → returns `""` | ✅ |
| All 27 `ToolMacroDefinition` constants | No | Constructed inline; `Map.of()` used for signals | ✅ |

---

## 3. `ToolMacroRegistry`

| Site | Nullable? | Handling | Status |
|---|---|---|---|
| `execute()` — `tool` lookup result | Yes | `tool == null` → returns `"Unknown tool: …"` | ✅ |
| `operationType()` — `tool` lookup result | Yes | `tool != null ? … : "UNKNOWN"` — never returns null | ✅ |
| `canonicalName()` — `toolName` param | Yes | `null`/blank → returns `""` | ✅ |
| `execute()` return value | No contract | Delegates to `ToolMacro.execute()` — see §6 | ⚠️ |

---

## 4. `ToolModule` interface

| Site | Nullable? | Handling | Status |
|---|---|---|---|
| `execute()` return value | Not specified | No `@NonNull`/`@Nullable` contract declared | ⚠️ |
| `operationCategoryFor()` default | No | Returns `getClass().getSimpleName()` — never null | ✅ |

**Recommendation:** add `@NonNull` (Jakarta or JSpecify) to `execute()`'s return type to
make the never-null contract explicit and enable static analysis.

---

## 5. `MacroToolModule`

| Site | Nullable? | Handling | Status |
|---|---|---|---|
| `embeddingService` (CDI constructor) | Yes — `Instance<>` may be unresolvable | `isResolvable()` guard before `.get()` | ✅ |
| `embeddingService` (package-private ctor) | Yes | `buildTools()` checks `!= null` before adding tools | ✅ |
| `pathSummaryLlmService` (CDI ctor) | Yes — `Instance<>` | Forwarded to `ExploreToolSupport`; checked with `isResolvable()` | ✅ |
| `projectDiscoveryService` (CDI ctor) | Yes — `Instance<>` | Forwarded to `ExploreToolSupport`; checked with `isResolvable()` | ✅ |
| `toolActivityService` (CDI ctor) | No — concrete CDI bean | Not null-checked; CDI guarantees injection | ✅ |
| `toolTelemetryService` (CDI ctor) | No — concrete CDI bean | Not null-checked; CDI guarantees injection | ✅ |
| `toolActivityService` / `toolTelemetryService` (WDS-only ctor) | **Yes — passed as `null`** | `IntrospectToolSupport` guards both fields before use | ✅ |
| `execute()` return | Delegates to registry | Registry returns non-null strings; catch blocks return non-null | ✅ |

---

## 6. `ToolMacro` implementations (all 25–27 tools)

| Pattern | Example sites | Status |
|---|---|---|
| `execute()` always returns a non-null `String` | All tools — path info, git log, read file, write file … | ✅ (by convention) |
| No `@NonNull` annotation on return | All implementations | ⚠️ — convention not enforced by compiler/analyser |
| `args` map value lookups — absent key | `ToolArguments.getString()` returns `defaultValue` | ✅ |
| Required arg missing | `ToolArguments.require()` throws `IllegalArgumentException` | ✅ — caught in `MacroToolModule.execute()` |
| Path `getFileName()` can be null | `ExploreToolSupport.addMatch()`, `PathAnalyzer` | ✅ — both check before use |

---

## 7. `ToolArguments`

| Method | Nullable input | Nullable return | Handling | Status |
|---|---|---|---|---|
| `parse(json)` | `json` nullable | Returns `Map.of()` for null/blank/unparseable | ✅ |
| `getString(args, key, default)` | `val` may be null | Returns `defaultValue` | ✅ |
| `require(args, key)` | n/a | Never null | throws on blank/absent | ✅ |
| `getInt(args, key, default)` | `value` may be null | Returns `defaultValue`; `-1` on parse error | ✅ |
| `getBoolean(args, key, default)` | `value` may be null | Returns `defaultValue` | ✅ |

---

## 8. `ToolService`

| Site | Nullable? | Handling | Status |
|---|---|---|---|
| `execute(request, memoryId)` — `request` param | Yes | Explicit `request == null` guard at entry | ✅ |
| `request.name()` | Yes | Checked `== null` before use | ✅ |
| `toolModules` (injected `Instance<>`) | Defensively assumed nullable | `toolModules == null ? List.of() : …` | ✅ |
| `toolTelemetryService` (injected) | Yes in test ctor | `== null` check in `recordTelemetry()` | ✅ |
| `resolvedToolModules` (volatile field) | Yes until initialised | Double-checked locking with `!= null` | ✅ |
| `classifyError(result)` return | Yes — returns `null` for success | Callers pass to `recordTelemetry` — handled by `ToolTelemetryService` | ✅ |
| `execute()` return | No | All code paths return a non-null string | ✅ |

---

## 9. Support classes

### `ExploreToolSupport`

| Site | Nullable? | Status |
|---|---|---|
| `pathSummaryLlmService` field | Yes (`Instance<>` or explicit null) | `!= null && isResolvable()` guarded ✅ |
| `projectDiscoveryService` field | Yes | `== null \|\| !isResolvable()` guarded ✅ |
| `llmResult` from `pathSummaryLlmService.get()` | Yes | `!= null` checked before use ✅ |
| `path.getFileName()` in walk callbacks | Yes (filesystem root) | null-checked in `addMatch()` and walk filter ✅ |

### `IntrospectToolSupport`

| Site | Nullable? | Status |
|---|---|---|
| `toolActivityService` field | Yes (null in test ctor) | `!= null` guarded before each use ✅ |
| `toolTelemetryService` field | Yes (null in test ctor) | `!= null` guarded before each use ✅ |
| `Project` / `ProjectKnowledge` Panache lookups | Return `null` when not found | All callers check before dereference ✅ |

### `WriteToolSupport`

| Site | Nullable? | Status |
|---|---|---|
| Panache `ProjectKnowledge` lookups | May return null | All checked before access ✅ |
| `knowledge.getJsonContent()` | Yes | Compound null check `knowledge == null \|\| getJsonContent() == null` ✅ |

### `ReadToolSupport`

| Site | Nullable? | Status |
|---|---|---|
| `explicitProjectDir` parameter | Yes | `!= null && isDirectory()` guarded ✅ |
| Panache lookups | May return null | Checked before access ✅ |

---

## 10. Embedding tools (`GetEmbeddingContextToolImpl`, `GetRecentEmbeddingsToolImpl`)

| Site | Nullable? | Status |
|---|---|---|
| `embeddingService` field | Guarded by `MacroToolModule` (only added when non-null) | Additional `== null` guard inside `execute()` ✅ |
| `match.sourceId()` / `embedding.sourceId()` | Yes | `!= null && !isBlank()` checked ✅ |
| `match.text()` / `embedding.text()` | Yes | `!= null` checked ✅ |

---

## Summary of risks

| Risk level | Count | Notes |
|---|---|---|
| ❌ Missing guard (NPE risk) | **0** | No unguarded null dereferences found |
| ⚠️ Undocumented / unannotated nullable | **3** | `ToolMacro.execute()` return, `ToolModule.execute()` return, `ToolMacroRegistry.execute()` return — non-null by convention but not compiler-enforced |
| ✅ Correctly handled | **50+** | All explicit null checks verified against their usage sites |

### Recommendation

Add a `@NonNull` return-type annotation (from `org.jspecify` or
`jakarta.annotation`) to `ToolMacro.execute()` and `ToolModule.execute()`.
This would propagate the existing informal convention into the type system,
enabling IDEs and static analysers (e.g. SpotBugs, NullAway) to enforce it
without requiring further runtime changes.
