# Architecture Overview

## Application Structure

The application is a Quarkus Picocli CLI tool that runs an AI-powered developer assistant in the terminal. It is composed of five main layers:

```
┌────────────────────────────────────────────────────────┐
│                     Entry Point                        │
│        PdhdLauncher / Main  →  MainMenu (root cmd)     │
└───────────────────────┬────────────────────────────────┘
                        │ subcommands
          ┌─────────────┼──────────────────────┐
          ▼             ▼                      ▼
   AssistantMenu  OllamaConfigMenu          DebugMenu / WebUiMenu / ExitCommand
          │
          │ (constructs)
          ▼
┌─────────────────────────────────────────────────────────┐
│              Chat Session Layer (menu package)          │
│  AssistantTerminalAdapter  ←→  AssistantInputAdapter    │
│          │                           │                  │
│  AssistantTranscript        AssistantSessionController  │
│  AssistantConversationRenderer                          │
│  AssistantViewport                                      │
└───────────────────────┬─────────────────────────────────┘
                        │
          ┌─────────────┼───────────────────────┐
          ▼             ▼                       ▼
   AssistantService  ModelConfigService   TelemetryService
   (LangChain4j)     (Panache/Postgres)   (Panache/Postgres)
```

---

## Package Map

| Package                         | Purpose                                             |
| ------------------------------- | --------------------------------------------------- |
| `ac.uk.sussex.kn253`            | Root: entry point, `MainMenu`                       |
| `ac.uk.sussex.kn253.menu`       | All TUI components: menus, adapters, renderers      |
| `ac.uk.sussex.kn253.events`     | CDI event records for cross-component signalling    |
| `ac.uk.sussex.kn253.services`   | Application-scoped business logic and CDI producers |
| `ac.uk.sussex.kn253.ollama`     | REST client and data types for the Ollama HTTP API  |
| `ac.uk.sussex.kn253.repository` | JPA entities and database access                    |
| `ac.uk.sussex.kn253.resources`  | JAX-RS REST endpoints (web UI back-end)             |
| `ac.uk.sussex.kn253.tools`      | LangChain4j tool-calling implementations            |

---

## CDI / Lifecycle Diagram

```
@ApplicationScoped TerminalLifecycleService
        │  @Produces @Named("mainTerminal")
        ▼
     Terminal  ──────────────────────────────────────────┐
        │ @Inject @Named("mainTerminal")                 │
        │                                                │
  AssistantMenu          OllamaConfigMenu           DebugMenu
  @ApplicationScoped     @ApplicationScoped         @ApplicationScoped
  @Command(assistant)    @Command(configure)        @Command(debug)
        │
        │  fires ──► ChatResizedEvent ──► @Observes onChatResized()
        │                                      │
        │  fires ◄── ChatRepaintEvent ◄─────────┘ fires
        │
        │  @Observes onChatRepaint()
        │      calls activeTerminalAdapter.render()
        ▼
  AssistantTerminalAdapter (implements Terminal)
```

The terminal is produced once via `TerminalLifecycleService` (which holds `@PreDestroy` shutdown), injected via `@Named("mainTerminal")` into every menu that needs it.

Browser-facing events (`ChatResizedEvent`, `ChatRepaintEvent`) travel through the CDI `Event<T>` bus rather than direct method calls, keeping menus decoupled.

---

## Chat Session — Class Relationships

```
                          ┌───────────────────────────┐
                          │      AssistantMenu         │
                          │  @ApplicationScoped        │
                          │                            │
                          │  + run()                   │
                          │  + onChatResized()         │
                          │  + onChatRepaint()         │
                          └────────────┬───────────────┘
                                       │ constructs
               ┌───────────────────────┼────────────────────────┐
               ▼                       ▼                        ▼
  ┌─────────────────────┐  ┌───────────────────────┐  ┌──────────────────────────┐
  │  AssistantInputAdapter│  │ AssistantTerminalAdapter│  │ AssistantSessionController│
  │  (Closeable)        │  │  (implements Terminal) │  │                          │
  │                     │  │                        │  │  coordinates the loop:   │
  │  - LineReader        │  │  - Terminal terminal    │  │  input → AI → output     │
  │  - scroll callbacks  │  │  - AssistantTranscript  │  │                          │
  │  - resize detection  │  │  - Renderer             │  │  references:             │
  │                     │  │  - frameTerminal (dumb)  │  │  InputAdapter            │
  │  fires:             │  │  - inputForwarder (pipe) │  │  TerminalAdapter         │
  │  ChatResizedEvent   │  │                        │  │  AssistantService        │
  └─────────────────────┘  │  + showUserMessage()    │  └──────────────────────────┘
                           │  + showAssistantMessage()│
                           │  + showStatus()          │
                           │  + scrollUp/Down()        │
                           │  + render()              │
                           │  + close()               │
                           └──────────┬───────────────┘
                                      │ owns
                     ┌────────────────┼───────────────────┐
                     ▼                ▼                   ▼
        ┌─────────────────────┐  ┌──────────────────┐  ┌───────────────────────────┐
        │  AssistantTranscript│  │ AssistantViewport │  │ AssistantConversationRenderer│
        │                     │  │  (record)         │  │  (interface)              │
        │  - entries: List<   │  │                   │  │                           │
        │    TranscriptEntry> │  │  wraps Terminal:  │  │  + renderLines(           │
        │  - scrollOffset     │  │  + width()        │  │      RenderLinesData)     │
        │  - statusLine       │  │  + contentWidth() │  │  + render(RenderData)     │
        │                     │  │  + bodyHeight()   │  │                           │
        │  record TranscriptEntry│ │                   │  │  ◄── DefaultRenderer     │
        │    (rolePrefix,text)│  │  validates min    │  │      (UnsupportedOperation│
        └─────────────────────┘  │  80×1 at construct│  │       Exception — stub)   │
                                 └──────────────────┘  └───────────────────────────┘
```

---

## AssistantTerminalAdapter — Internal Pipe Architecture

The adapter maintains an internal JLine "dumb" frame terminal that is used to buffer and normalise text output from LangChain4j streaming into `TranscriptEntry` records. The real terminal is used for rendering.

```
  showUserMessage() / showAssistantMessage()
          │
          │ writes to
          ▼
  frameTerminal.writer()  (JLine dumb terminal)
          │
          │ output via FrameOutputStream
          ▼
  ┌─────────────────────────────────┐
  │  FrameOutputStream (inner class)│
  │  buffers bytes → on '\n':       │
  │    transcript.append(...)       │
  │    render()                     │
  └──────────────────┬──────────────┘
                     │ triggers
                     ▼
               render()
                 │
                 ├── new AssistantViewport(terminal)
                 ├── renderer.renderLines(RenderLinesData)
                 ├── renderer.render(RenderData)
                 └── terminal.writer().print(...)   ← real terminal output


  forwardInput(text) → PipedOutputStream → PipedInputStream → frameTerminal.input()
                        (inputForwarder)             (inputStream)
```

`DefaultAssistantConversationRenderer` holds its own dumb terminal wired to `terminal.output()` (the real terminal's raw output stream). Both `renderLines` and `render` currently throw `UnsupportedOperationException` — the interface contract is defined, the implementation is pending.

---

## Renderer Data Flow

```
  AssistantTranscript.entries()          AssistantViewport.contentWidth()
          │                                         │
          └─────────── RenderLinesData ─────────────┘
                                │
                                ▼
              renderer.renderLines(RenderLinesData)
                                │
                                ▼
                  List<RenderedTranscriptLine>
                                │
          ┌─────────────────────┼──────────────────────────────────┐
          │                     │                                  │
  statusLine            AssistantViewport              scrollOffset / maxOffset
          │                     │                                  │
          └─────────────────────┼──────────────────────────────────┘
                                │ RenderData
                                ▼
                  renderer.render(RenderData)
                                │
                                ▼
                  AttributedStringBuilder  →  toAnsi(terminal)  →  terminal.writer()
```

---

## Event Records

```
events package
├── ChatResizedEvent(previousWidth, previousHeight, width, height)
│     fired by: AssistantInputAdapter (on terminal size change)
│     observed by: AssistantMenu.onChatResized()
│
├── ChatRepaintEvent(reason)
│     fired by: AssistantMenu.onChatResized() (cascades)
│     observed by: AssistantMenu.onChatRepaint() → activeTerminalAdapter.render()
│
└── CwdResolvedEvent(requestedPath, resolvedPath)
      fired by: AssistantWorkingDirectoryService
      observed by: (REST / WebUI consumers)
```

---

## Menu Hierarchy (Picocli)

```
MainMenu  (@Command root)
├── AssistantMenu      (@Command "assistant")
├── OllamaConfigMenu   (@Command "configure")
├── DebugMenu          (@Command "debug")
├── WebUiMenu          (@Command "webui")
└── ExitCommand        (@Command "exit")
```

Each subcommand is `@ApplicationScoped` with full constructor injection. The `MainMenu` sets up the picocli `CommandLine` with `IFactory` for CDI-aware subcommand resolution.

---

## Services Layer

| Service                            | Scope                | Responsibility                                                     |
| ---------------------------------- | -------------------- | ------------------------------------------------------------------ |
| `TerminalLifecycleService`         | `@ApplicationScoped` | Produces `@Named("mainTerminal")` Terminal; `@PreDestroy` teardown |
| `AssistantService`                 | `@ApplicationScoped` | LangChain4j AI chat orchestration                                  |
| `ModelConfigService`               | `@ApplicationScoped` | Reads/writes model configuration (Panache)                         |
| `OllamaManagementService`          | `@ApplicationScoped` | Ollama REST operations (list, pull, delete, etc.)                  |
| `TelemetryService`                 | `@ApplicationScoped` | Records model call metrics                                         |
| `AssistantWorkingDirectoryService` | `@ApplicationScoped` | Tracks current working directory for context                       |
| `CwdService`                       | (scoped)             | Shared cwd state for REST + CLI layers                             |
| `WebUiService`                     | `@ApplicationScoped` | Manages embedded web UI lifecycle                                  |
| `RuntimeManagementService`         | `@ApplicationScoped` | Application shutdown utilities                                     |
