# Ollama Fallback and Provisioning History

## Status

This document is retained only as a pointer to removed behavior.

The Testcontainers fallback and startup model-provisioning workflow described by earlier revisions was removed from the checked-in implementation. Current startup code does not auto-start an internal Ollama container and does not pull missing models during bootstrap.

## Historical Note

Earlier April 2026 revisions expected the bootstrap path to do all of the following before Quarkus CDI came online:

- start a Testcontainers-backed Ollama instance when no external endpoint was usable
- prompt in production before enabling that fallback
- ensure chat and embedding models were present, pulling them if necessary
- let the `configure` command continue with warnings on some failures

That behavior is no longer present.

## Current Replacement

The current bootstrap path is documented in [docs/operation-summary-2026-04-09.md](operation-summary-2026-04-09.md) and implemented in:

- `src/main/java/ac/uk/sussex/kn253/PdhdLauncher.java`
- `src/main/java/ac/uk/sussex/kn253/bootstrap/PreCdiOllamaBootstrap.java`

Today the bootstrap behavior is limited to:

1. Skip startup probing for help/version commands.
2. Resolve `pdhd.ollama.base-url` from configuration.
3. Fail fast if the base URL is missing or `/api/tags` is unreachable.
4. Publish `pdhd.ollama.base-url` and `pdhd.ollama.bootstrap.base-url` as system properties on success.

There is no container fallback, no model pull, and no production confirmation prompt.
