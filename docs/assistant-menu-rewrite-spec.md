# Assistant Menu Rewrite Spec

## Purpose

This document defines the expected behaviour of the terminal assistant menu and records the current architecture review before any larger rewrite.

It is intended to be the baseline for a `1.0.0`-ready assistant TUI implementation.

## Expected Behaviour

### Entry And Exit

- Entering the assistant opens a dedicated assistant conversation view.
- Exiting the assistant returns control to the main menu without crashing the application.
- `Ctrl+C`, EOF, or typing `exit` must terminate the assistant loop cleanly.
- Returning from the assistant must not destroy prior terminal scrollback.

### Prompt And Input

- The input prompt shows the shortened working directory only, in the form `path > `.
- The assistant model name is not repeated in the prompt if it is already shown elsewhere in the view.
- Blank input is ignored.
- User input is added to the visible conversation transcript before the model reply is rendered.

### Conversation View

- The assistant conversation is rendered inside a bordered viewport.
- The viewport must wrap long lines to the available content width.
- The viewport footer shows status information when available.
- The viewport preserves a transcript of prior user and assistant messages for the current session.

### Scrolling

- The transcript is scrollable with mouse wheel events when the terminal supports mouse tracking.
- Wheel up scrolls older transcript entries into view.
- Wheel down returns toward the latest transcript entries.
- New assistant or user messages reset the viewport to the bottom.

### Status And Progress

- Before a reply is available, the assistant view shows a transient thinking state.
- When a reply completes, the footer shows response statistics such as status, tokens, and elapsed time.
- Clearing or redrawing the frame must preserve prior terminal history in scrollback before the visible screen is cleared.

### Terminal History Preservation

- Before any assistant frame redraw or menu screen clear, the main terminal is scrolled by roughly one visible page.
- This preserves warnings, exceptions, and previous UI states in terminal scrollback.
- Screen redraws should not silently erase operational output.

### Errors

- Exceptions raised during assistant calls must remain visible in terminal scrollback.
- The assistant loop must still record telemetry even when model invocation fails.
- Terminal rendering failures should fail fast rather than leave the application in a partially interactive state.

### Telemetry

- Each assistant request records:
  - model name
  - current working directory
  - request text
  - response text when available
  - estimated request/response token counts
  - duration
  - error class when the call fails

## Current Architecture

### Main Components

- `MainMenu`
  - Owns the application terminal.
  - Launches the assistant subcommand and other menus.
  - Applies terminal clearing on some menu transitions.

- `AssistantMenu`
  - Activates request context.
  - Resolves CDI dependencies.
  - Creates the `LineReader` used for the assistant prompt.
  - Runs the main conversation loop.

- `AssistantPrompt`
  - Adapts the `LineReader` prompt to the assistant view.
  - Handles mouse widget registration and scroll events.
  - Delegates conversation rendering to `ConversationFrame`.

- `ConversationFrame`
  - Stores transcript lines.
  - Owns the bordered viewport rendering.
  - Owns the helper-specific dumb terminal bridge.
  - Owns the frame redraw path.

- `AssistantService`
  - Pure chat service boundary.
  - Returns a plain `String` response for each user request.

- `AssistantWorkingDirectoryService`
  - Provides the current cwd for prompt rendering and telemetry.

- `TelemetryService`
  - Records model call telemetry for each assistant request.

## Architecture Review

### Finding 1

The current assistant stack mixes orchestration, UI state, terminal emulation, and rendering in one file.

Impact:

- `AssistantMenu` currently contains lifecycle logic, prompt logic, terminal glue, rendering helpers, scrolling logic, and transcript storage in nested classes.
- This makes the assistant menu hard to test in slices and expensive to rewrite safely.

Recommended direction:

- Split into explicit layers:
  - controller or coordinator
  - transcript model
  - renderer
  - terminal adapter
  - input adapter

### Finding 2

The helper-owned dumb terminal is conceptually interesting but still architecturally ambiguous.

Impact:

- `ConversationFrame` now owns a synthetic JLine terminal, but the primary interaction still depends on the main `LineReader` terminal.
- This means there are effectively two terminal abstractions active in the same assistant flow.
- Input forwarding exists, but command ownership and rendering ownership are split.

Recommended direction:

- Choose one terminal authority for the assistant rewrite.
- Either:
  1. the assistant owns a single dedicated terminal abstraction end-to-end, or
  2. the assistant renders into the main terminal and removes the synthetic terminal bridge.

### Finding 3

Transcript state is currently duplicated conceptually.

Impact:

- The transcript is stored in `lines`, while helper terminal output can also append into the same model through the output bridge.
- This creates risk of drift, double writes, and ordering bugs.

Recommended direction:

- Define one canonical transcript model.
- All user messages, assistant messages, system messages, and status updates should flow through the same append API.

### Finding 4

Screen clearing is a global side effect and should be treated as infrastructure, not widget logic.

Impact:

- The assistant frame redraw path currently depends on menu-level terminal clearing behaviour.
- This couples rendering with scrollback preservation policy.

Recommended direction:

- Encapsulate terminal page preservation and screen clear semantics in a dedicated terminal adapter.
- The renderer should request `redraw frame`, not `clear terminal and repaint` directly.

### Finding 5

Test coverage for the assistant interaction model is currently too narrow for a full rewrite.

Impact:

- Current tests only validate prompt formatting helpers.
- There is no focused test coverage for:
  - transcript append rules
  - frame rendering
  - mouse scrolling
  - redraw behaviour
  - terminal history preservation
  - error visibility

Recommended direction:

- Before a large rewrite, add tests around:
  - transcript model behaviour
  - viewport calculations
  - redraw output formatting
  - terminal adapter behaviour

## Proposed Rewrite Boundaries

The rewrite should aim for these top-level types:

- `AssistantSessionController`
  - Runs the conversation loop.
  - Talks to `AssistantService`, `TelemetryService`, and cwd/model providers.

- `AssistantTranscript`
  - Canonical in-memory conversation model.
  - No terminal logic.

- `AssistantViewport`
  - Pure viewport math: wrapping, visible range, scroll position, footer state.

- `AssistantRenderer`
  - Converts transcript plus viewport state into JLine attributed output.

- `AssistantTerminalAdapter`
  - Encapsulates terminal clear, scrollback preservation, mouse support, and prompt wiring.

## Rewrite Acceptance Criteria

- No nested class should need to emulate its own terminal unless that terminal is the sole interaction surface.
- Transcript state must have one source of truth.
- Rendering must be deterministic from transcript plus viewport state.
- Prompt handling and frame rendering must be testable without running the real assistant model.
- Exceptions and warnings must remain visible in terminal scrollback.
- Mouse-wheel scrolling must remain supported when the terminal supports it.
