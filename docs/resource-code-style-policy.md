# Backend Resource Code Style Policy

**Applies to:** `src/main/java/ac/uk/sussex/kn253/resources/*.java`  
**Primary package:** `ac.uk.sussex.kn253.resources`

## Scope

This policy defines style guardrails for HTTP resource classes only (JAX-RS endpoints in `ac.uk.sussex.kn253.resources`).

It focuses on five recurring issues:

- wildcard imports
- Jakarta annotation consistency
- endpoint handler naming clarity
- `@Path` usage consistency
- pull-request and CI review checks

## Rules

1. No wildcard imports in resource classes.

- Do not use `import ...*;` (for example `jakarta.ws.rs.*` or `java.util.*`).
- Use explicit imports for all types and annotations.

2. Use Jakarta imports consistently.

- Resource annotations and related APIs must come from `jakarta.*`.
- Do not introduce `javax.*` imports in resource classes.

3. Use API-action oriented handler method names.

- Name endpoint handler methods after API behavior, not generic POJO-style names.
- Prefer names that communicate the endpoint action clearly (for example `streamProjectOutput`, `deleteConversationById`) over vague names (for example `handle`, `process`, `execute`).
- Keep names aligned with endpoint intent and route semantics so reviews can map behavior quickly.

4. Keep `@Path` usage consistent and minimal at method level.

- Class-level `@Path` must define the API base route and optional suffix capture (current convention: `"/api/<namespace>{operation: (/.*)?}"`).
- Prefer class-level `@Path` as the route declaration when feasible (one resource class per route).
- Use method-level `@Path` only when needed for multiple sub-routes in one resource class.
- Method-level `@Path` values are relative segments and must not repeat the `/api` prefix.
- Keep one leading slash style for method-level paths (for example `@Path("/stream")`, `@Path("/{id}/file")`).
- This approach allows direct use/import of `java.nio.file.Path` without constant disambiguation.
- When disambiguation is needed, prefer qualifying the JAX-RS annotation once at class level (`@jakarta.ws.rs.Path`) to avoid `Path` clashes.

5. Review/CI must enforce these checks.

- PR review should treat violations as blocking.
- CI or pre-merge validation should fail if wildcard imports or `javax.*` imports are present in resource classes.

## Pull Request Checklist

- [ ] No `*` imports in `src/main/java/ac/uk/sussex/kn253/resources/*.java`.
- [ ] No `javax.*` imports in `src/main/java/ac/uk/sussex/kn253/resources/*.java`.
- [ ] Endpoint handler method names are API-action oriented and reflect endpoint behavior (not generic POJO-style names).
- [ ] Class-level `@Path` is the primary route declaration when feasible (one resource class per route).
- [ ] Method-level `@Path` is used only for sub-routes within a class, does not repeat `/api`, and avoids `Path` name clashes (prefer one class-level `@jakarta.ws.rs.Path` qualification when needed).
- [ ] Added/changed endpoints follow existing route naming patterns used in `ac.uk.sussex.kn253.resources`.

## Fast Local Checks

Run from repository root.

```bash
# 1) Find wildcard imports in resource classes (should return nothing)
rg -n '^import\s+.+\.\*;' src/main/java/ac/uk/sussex/kn253/resources

# 2) Find javax imports in resource classes (should return nothing)
rg -n '^import\s+javax\.' src/main/java/ac/uk/sussex/kn253/resources

# 3) Check class-level @Path values in resources
rg -n '^@jakarta\.ws\.rs\.Path\("/api/.+"\)' src/main/java/ac/uk/sussex/kn253/resources

# 4) Check method-level @Path values that incorrectly include /api (should return nothing)
rg -n '^\s+@Path\("/api/.+"\)' src/main/java/ac/uk/sussex/kn253/resources
```

Optional `grep` equivalents:

```bash
grep -RInE '^import\s+.+\.\*;' src/main/java/ac/uk/sussex/kn253/resources
grep -RInE '^import\s+javax\.' src/main/java/ac/uk/sussex/kn253/resources
grep -RInE '^\s+@Path\("/api/.+"\)' src/main/java/ac/uk/sussex/kn253/resources
```

## Exceptions

Allowed only with a short justification in the PR description:

- Temporary exception during staged refactors where all affected resource files are fixed in the same PR.
- Third-party generated code copied into resources (avoid when possible); annotate clearly and isolate changes.

No exception is allowed for introducing new `javax.*` imports in resource classes.
