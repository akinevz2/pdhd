# Known Issues and Observations

## Runtime Reliability Notes (2026-04-05)

### Resolved: Ollama REST Client CDI Injection Failure in CLI Flow

**Symptom**: Runtime error while entering configuration actions:

`IllegalArgumentException: Unable to determine the proper baseUrl/baseUri...`

**Cause**: Startup-time `@RestClient` injection was brittle in menu/CLI call paths where runtime base URL and extension wiring could drift.

**Current State**:

- `OllamaManagementService` now resolves/builds clients dynamically from base URL at call time.
- Missing/blank URL is treated as invalid and fails fast in client resolution.

**Verification**:

- Added unit coverage in `src/test/java/ac/uk/sussex/kn253/services/OllamaManagementServiceTest.java` for provided URL use, fallback behavior, and missing-config behavior.

### Remaining Gap: Documentation Deliverables

The documentation plan still references deliverables that are not yet present as first-class docs:

- `quick-start.md`
- `developer-guide.md`
- Dedicated API contract/spec publication

## Folder Summary Tool Evidence Leakage

**Issue**: When calling `read_folder_manifest` to generate folder summaries, the tool returns evidence-tagged content (e.g., "=== sampled file contents (evidence only) ===") that is intended as internal AI reasoning context. However, when this evidence reaches the LLM for folder summarization, the model may reproduce or reference this scaffolding text directly in the final response, which then appears in the frontend UI.

**Impact**: Folder summaries in the explorer canvas inadvertently display internal tool evidence markers and truncation notices, degrading user experience by exposing implementation details.

**Root Cause**:

- `IntrospectToolSupport.readFolderManifest()` returns structured evidence with labeled sections
- `openFolderSummary()` in App.tsx sends raw `read_folder_manifest` output as context to the LLM
- The LLM response is rendered directly without filtering
- The model reproduces or includes evidence artifacts in its reply

**Recommendation**:

1. Strip evidence scaffolding before sending to LLM, or
2. Post-process LLM response to remove evidence markers before display, or
3. Add instruction to LLM system prompt to never include evidence headers in final summary

---

## Frontend UI Integration Gap: Navigation

**Issue**: The "up/parent directory" button appears as a dedicated, visually separate button above the folder listing, rather than being integrated as a ".." entry in the folder list itself like traditional file browsers.

**Impact**:

- UI inconsistency with standard file explorer patterns
- Additional dedicated button takes up visual real estate
- Folder structure hierarchy not fully represented in the listing

**Location**:

- Browser row rendering in `App.tsx` (lines ~740+)
- The ".." entry is hard-coded as a separate `browser-row` div

**Current Behavior**:

```tsx
<div className="browser-row">
  <button
    className="browser-row-main"
    onClick={() => {
      setWorkingFolder("..").catch(() => {});
    }}
    disabled={cwdUpdating}
    title="Parent directory"
  >
    <span className="browser-row-icon" aria-hidden="true">
      ▸
    </span>
    <span className="browser-row-name">..</span>
  </button>
</div>
```

**Expected Behavior**: ".." entry should be interleaved with folder list entries, sorted naturally or placed at the top of the listing.

---

## Frontend UI Integration Gap: Explore Button

**Issue**: There is no visible "Explore" or "Open in Canvas" button for quick access to opening folders/files in the explorer window from the filesystem browser.

**Impact**: Users must either:

- Manually alt-tab or arrange windows to see both browser and explorer
- Use the canvas auto-open feature on activity items (if triggered)
- Directly click tree items (which works, but isn't discoverable)

**Expected Behavior**: Folders and files in the left-pane browser should have secondary action buttons (e.g., "→ Explore" icon) to open them in the floating explorer canvas on demand.

---

## Tool Error Propagation

**Status**: Recently refactored to throw exceptions instead of returning null values

Cache operations (`cacheFileContent`, `cachePathAnalysis`, `cacheFolderManifest`) now throw `UnsupportedOperationException` when CDI persistence context is unavailable. This exception is caught at call sites but not always logged to the user.

**Observation**: Errors are silently swallowed in production use, hiding failures from developers/report writers.
