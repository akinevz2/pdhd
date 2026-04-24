# PDHD Project Manifest

## Core Purpose
This document serves as the official 'Source of Truth' for the PDHD (Project Development Hub) repository. It documents the project's high-level functional goals, the technical components involved, and the relationships between them.

## Project Vision
[Write a high-level, human-readable description of the project's purpose here. What is the outcome? E.g., "A model-agnostic platform for adaptive academic research simulation."]

## Key Components & Modules
List all major service modules (e.g., `DataIngestionService`, `LLMAdapter`, `ReportGenerationManager`).

| Module Name | Responsibility | Location (Directory) | Current Status | Owner |
| :--- | :--- | :--- | :--- | :--- |
| `LLMAdapter` | Provides the abstract interface for LLM calls, handling model shifting. | `src/adapters/` | **Critical:** Under active stabilization. | [Your Name] |
| `DataIngestor` | Handles all data fetching and cleansing. | `src/ingest/` | Stabilization Required. | [Team Member] |
| `UI/WebUI` | The frontend interaction layer (browser). | `src/webui/` | **WARNING:** Manual inconsistencies found (P1). | [Your Name] |

## Version Control Invariants
*   **Development Workflow:** All new features must be documented in `docs/plan/` before implementation.
*   **Artifact Linking:** Every change must update both the corresponding feature's `specs/` file AND this manifest.

## To Build/Run
```bash
# Development environment startup
./mvnw quarkus:dev
```