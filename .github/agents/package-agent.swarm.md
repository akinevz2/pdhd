# Package Agent Swarm

This file tracks the PDHD package-maintainer agents and the supporting agents that work alongside them.

## Purpose

- Provide a single index of package ownership across backend and frontend source areas.
- Make it clear which agent should be used for maintenance, refactoring, and documentation cleanup in each package.
- Record the cross-cutting agents that coordinate documentation, consistency review, and RAG-specific integration work.

## Shared Maintainer Rules

All package-maintainer agents are expected to follow the same common intelligence baseline:

- Read relevant material in `docs/` before editing code.
- Treat documentation as intent and the current codebase as implementation evidence.
- Evict stale documentation when code and docs diverge and the code evidence is clear.
- Ask before implementing features or behaviors not already addressed in docs or the current codebase.
- Keep refactors local to the managed package and preserve external contracts unless the user approves a change.

## Backend Package Agents

| Agent file                                    | Managed package or surface                            | Primary responsibility                            |
| --------------------------------------------- | ----------------------------------------------------- | ------------------------------------------------- |
| `pdhd-backend-entrypoint-maintainer.agent.md` | `ac.uk.sussex.kn253`                                  | Root backend entrypoint and launcher maintenance  |
| `pdhd-bootstrap-maintainer.agent.md`          | `ac.uk.sussex.kn253.bootstrap`                        | Startup wiring and bootstrap maintenance          |
| `pdhd-commands-maintainer.agent.md`           | `ac.uk.sussex.kn253.commands`                         | CLI command and menu command maintenance          |
| `pdhd-events-maintainer.agent.md`             | `ac.uk.sussex.kn253.events`                           | Event flow and event contract maintenance         |
| `pdhd-ollama-maintainer.agent.md`             | `ac.uk.sussex.kn253.ollama`                           | Ollama integration maintenance                    |
| `pdhd-repository-maintainer.agent.md`         | `ac.uk.sussex.kn253.repository`                       | Persistence, entity, and cache-facing maintenance |
| `pdhd-resources-maintainer.agent.md`          | `ac.uk.sussex.kn253.resources`                        | REST resource and API maintenance                 |
| `pdhd-services-maintainer.agent.md`           | `ac.uk.sussex.kn253.services` excluding `services.ai` | Non-AI service orchestration maintenance          |
| `pdhd-services-ai-maintainer.agent.md`        | `ac.uk.sussex.kn253.services.ai`                      | AI-service orchestration maintenance              |
| `pdhd-support-maintainer.agent.md`            | `ac.uk.sussex.kn253.support`                          | Support-class maintenance                         |
| `pdhd-tools-maintainer.agent.md`              | `ac.uk.sussex.kn253.tools`                            | Tool dispatch and tool implementation maintenance |
| `pdhd-util-maintainer.agent.md`               | `ac.uk.sussex.kn253.util`                             | Utility and rendering helper maintenance          |
| `pdhd-websocket-maintainer.agent.md`          | `ac.uk.sussex.kn253.websocket`                        | WebSocket and streaming contract maintenance      |

## Frontend Package Agent

| Agent file                                | Managed package or surface | Primary responsibility                                                                       |
| ----------------------------------------- | -------------------------- | -------------------------------------------------------------------------------------------- |
| `pdhd-webui-frontend-maintainer.agent.md` | `src/main/webui/src`       | Frontend package maintenance for React, signals, modules, hooks, and API-facing UI contracts |

## Cross-Cutting Support Agents

These are not single-package owners, but they are part of the broader maintenance swarm:

| Agent file                            | Scope                                 | Role                                                                                           |
| ------------------------------------- | ------------------------------------- | ---------------------------------------------------------------------------------------------- |
| `pdhd-docs-spec-maintainer.agent.md`  | `docs/`                               | Maintains PDHD documentation and specification files in report-importable format               |
| `pdhd-consistency-reviewer.agent.md`  | Read-only review across docs and code | Audits documentation-code drift and architecture/API mismatches                                |
| `pdhd-rag-ollama-maintainer.agent.md` | Cross-package RAG surface             | Maintains Ollama-backed RAG behavior across `ollama`, `services.ai`, `tools`, and `repository` |
| `feature-report-writer.agent.md`      | Repository snapshot surface           | Captures repository snapshots, validation results, commit messages, and regression status      |

## Routing Guidance

- Use the package maintainer that most closely matches the package being edited.
- Use `pdhd-docs-spec-maintainer.agent.md` when the task is documentation-first rather than package-first.
- Use `pdhd-consistency-reviewer.agent.md` when the task is review or audit rather than implementation.
- Use `pdhd-rag-ollama-maintainer.agent.md` when a change spans retrieval, embeddings, Ollama integration, AI services, and cached project knowledge.
- Use `feature-report-writer.agent.md` when the task is to snapshot repository state, run validation, assess regressions, and prepare a commit.

## Maintenance Notes

- When adding a new package-maintainer agent, register it here in the appropriate section.
- When removing or renaming an agent, update this file in the same change.
- Keep filenames, package ownership, and cross-cutting roles consistent with the actual agent files under `.github/agents/`.
