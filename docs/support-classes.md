# Support Classes Guide

This project uses `*Support` classes to centralize shared literals and small cross-cutting rules so feature code stays focused on behavior.

## Current Support Classes

- `ac.uk.sussex.kn253.schema.SchemaKeys`
  - Canonical JSON/API key names.
- `ac.uk.sussex.kn253.schema.ToolSupport`
  - Shared tool/API non-key values (for example, absent markers).
- `ac.uk.sussex.kn253.schema.BackendSupport`
  - Shared backend constants for API/service behavior, including repository link rules and reused error strings.

## Usage Rules

When adding backend features:

1. Put repeated string literals in the appropriate `*Support` class.
2. Keep feature logic in services/resources, not in support classes.
3. Use support constants for:
   - host/protocol checks
   - regex fragments and marker names
   - stable user-facing error text reused across endpoints
4. Avoid ad-hoc literals in controllers/services if the value is reused or defines a project policy.

## Why This Exists

- Improves consistency across API and service layers.
- Reduces drift when behavior is updated in one place.
- Makes future feature toggles and compatibility work safer.

## Future Feature: Any Git Host Repository Links

Current behavior packages a repository webpage link for GitHub repository roots.

To support additional hosts (for example GitLab, Bitbucket, self-hosted forges) without spreading literals through services:

1. Add host/protocol/path policy constants to `BackendSupport`.
2. Expand repository URL validation in `RepoService` using those constants.
3. Keep API payload shape stable (`repoUrl` or absent marker).
4. Add host-specific tests for accepted and rejected URL shapes.

Suggested staged rollout:

1. Add passive acceptance for additional hosts (read-only link display).
2. Add explicit host policy constants and tests.
3. Add host-level config if needed (allow-list / deny-list).

This approach keeps new host support as a service-layer change while preserving frontend simplicity.
