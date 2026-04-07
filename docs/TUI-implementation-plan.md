# Plan: ChatTUI stream ownership + echo-off

Superseded status:

- This document describes the earlier `ChatTUI` multi-stream experiment.
- The active assistant implementation now uses the main terminal, a regular JLine `LineReader`, and `printAbove` for transcript output.
- Keep this document only as historical context while any `ChatTUI` compatibility code remains in the tree.

## Goal

Give `OutputPane` a `BufferedOutputStream` and `InputField` a `BufferedInputStream`, wire them as I/O for a new `ChatTUI`-owned `chatTerminal`, disable echo on `mainTerminal`, and use existing `ChatRepaintEvent` for repaint notification.

## File

`src/main/java/ac/uk/sussex/kn253/menu/ChatTUI.java`

---

## Phase 1 — `InputField` record: replace ByteBuffer with streams

- Remove `@SessionScoped` annotation (wrong on inner record)
- Remove `ByteBuffer inputBuffer` component
- Add `BufferedInputStream inputStream` — wraps a `PipedInputStream` (the read-end the chatTerminal reads from)
- Add `PipedOutputStream inputSink` — the write-end where mainTerminal keystrokes are fed in
- Both components created by ChatTUI constructor and passed in; `InputField` owns closing responsibility

## Phase 2 — `OutputPane` record: add streams

- Add `BufferedOutputStream outputStream` — wraps a `PipedOutputStream` (the write-end the chatTerminal writes AI output to)
- Add `PipedInputStream outputSource` — the read-end OutputPane uses in `paint()` for rendering content inside the box

## Phase 3 — `ChatTUI` constructor + new fields

- Add field `private final Terminal chatTerminal`
- Add field `private final Attributes savedMainTerminalAttrs` (for echo restore)
- Constructor:
  1. Create piped pairs:
     - `PipedOutputStream inputSink = new PipedOutputStream()` + `PipedInputStream inputPipe = new PipedInputStream(inputSink)` → for InputField
     - `PipedOutputStream outputPipe = new PipedOutputStream()` + `PipedInputStream outputSource = new PipedInputStream(outputPipe)` → for OutputPane
  2. Construct records:
     - `this.inputField = new InputField(this, new BufferedInputStream(inputPipe), inputSink)`
     - `this.outputPane = new OutputPane(this, new BufferedOutputStream(outputPipe), outputSource)`
  3. Build chatTerminal:
     - `TerminalBuilder.builder().dumb(true).streams(inputField.inputStream(), outputPane.outputStream()).build()`
  4. Echo-off on mainTerminal:
     - `this.savedMainTerminalAttrs = terminal.getAttributes()`
     - Clone attrs, set `LocalFlag.ECHO = false`, apply with `terminal.setAttributes(...)`
  5. Wrap IOException from PipedInputStream connect + TerminalBuilder.build() in `IllegalStateException`

## Phase 4 — `close()` update

- Restore echo: `terminal.setAttributes(savedMainTerminalAttrs)`
- Close chatTerminal in try-catch (best-effort)

## Phase 5 — Add `inputBufferHeight()` (fixes existing compile error)

- Add `int inputBufferHeight() { return 3; }` to ChatTUI (1 content line + 2 border lines)

## Phase 6 — Imports

- Add: `BufferedInputStream`, `BufferedOutputStream`, `PipedInputStream`, `PipedOutputStream`, `IOException` (java.io)
- Add: `Attributes` (org.jline.terminal), `TerminalBuilder` (org.jline.terminal)
- Remove: `ByteBuffer` (java.nio)

## ChatRepaintEvent wiring (no change needed)

- Existing `repaint()` → `dispatch(element)` → fires `ChatRepaintEvent(element)` → `onChatRepaint` → `redrawAll()`
- Children call `repaint()` after data changes — this mechanism is already correct

## Verification

1. `mvn compile -pl .` — no compile errors
2. Confirm `inputField.inputStream()` and `outputPane.outputStream()` resolve to correct types
3. Confirm `chatTerminal` is built successfully in test run or via manual inspection

## Scope exclusions

- Keyboard reading loop (writing mainTerminal keystrokes → inputSink) — not in scope
- OutputPane.paint() rendering content from outputSource — structural wiring only
- StatusBar compile issues (if any) — not touched

## Decisions

- `PipedOutputStream inputSink` is a component of `InputField` (not ChatTUI) so InputField can expose the write-end for a future key-reading loop
- Echo-off happens in constructor (not onChatOpened) since ChatTUI is @Singleton and currently no lifecycle distinction
- `inputBufferHeight()` returns 3 as a fixed stub; can be made dynamic later

## Next steps

- Add keyboard reading loop to TODO.md, writing keystrokes to `inputField.inputSink()`
- Add TODO to TODO.md to implement OutputPane.paint() to read from `outputPane.outputSource()` and render content
- Add TODO to TODO.md for error handling/logging around stream operations as needed
- Add TODO to TODO.md for unit tests for stream wiring if feasible (may require refactoring for testability)
- Add TODO to analyse completeness of StatusBar implementation and fix any compile issues there
