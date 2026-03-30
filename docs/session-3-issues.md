# Session 3 Implementation Summary: Issues and Observations

**Date**: March 30, 2026  
**Focus**: Error propagation refactoring, folder summary testing, and UI integration gaps

---

## 1. Folder Summary Tool Evidence Leakage

### Problem Statement

Folder summaries displayed in the explorer canvas contain internal tool evidence markers and scaffolding text designed for LLM reasoning, not end-user consumption.

### Root Cause Chain

1. `IntrospectToolSupport.readFolderManifest()` returns structured evidence with labeled sections:
   - "=== folder entries (recursive) ==="
   - "=== sampled file contents (evidence only) ==="
   - Truncation notices for large files
2. `App.tsx` `openFolderSummary()` passes this raw output as context to the LLM:

   ```ts
   const message = `Summarise the contents of this folder: ${folderPath}\n...
     First call read_folder_manifest with this exact folder path and use its evidence as the primary source.`;
   ```

3. The LLM, instructed to use the evidence, frequently reproduces or references these markers in its response

4. The response is rendered directly in the canvas without post-processing, exposing implementation details to users

### Impact

- **UX Degradation**: Users see internal scaffolding ("evidence only", truncation notices)
- **Trust Loss**: Appearance of incomplete or machine-generated content
- **Not Representative of Design**: Folder summaries are meant to be polished, user-friendly analysis

### Recommended Fix (Prioritized)

**Option A: Response Post-Processing (Lower Effort)**

- After receiving LLM response, strip evidence headers before display
- Regex patterns: `/^===.*?===\n/gm`, `/\(evidence only\)/gi`, `/\.\.\.\(truncated\)/gi`
- Implement in `openFolderSummary()` catch block or result processing

**Option B: Frontend-Backend Separation (More Robust)**

- Create a new backend endpoint or response wrapper that separates evidence from narrative
- Have the backend return `{ evidence: string, summary: string }`
- LLM processes evidence internally but only narrative reaches frontend
- Requires API contract change and AssistantChatResponse extension

**Option C: LLM System Prompt Clarification (Low Effort)**

- Adjust system prompt to instruct: "Never include evidence section headers or truncation notices in your final summary. Extract facts and present only polished analysis."
- Often sufficient, but depends on model capabilities

---

## 2. Frontend File Browser UI Integration Gaps

### Gap 1: Parent Directory Button Isolation

**Current State**:

```tsx
<div className="browser-row">
  <button className="browser-row-main" onClick={...}>
    <span className="browser-row-icon">▸</span>
    <span className="browser-row-name">..</span>
  </button>
</div>
```

Rendered as a dedicated, visually separate control above the folder listing.

**Expected State**:
".." entry integrated into the folder list as a first-class citizen, sorted at the top or naturally mixed with other entries.

**Location**: `App.tsx` lines ~708–718 (hardcoded `browser-row` before the entries loop)

**Rationale**:

- Consistent with standard file browser UX (Windows Explorer, Finder, VS Code)
- ".." is a valid filesystem entry, not a special action button
- Reduces visual clutter and UI learning curve

**Effort**: Low (~10 lines change, mostly reordering render logic)

---

### Gap 2: Missing "Explore" Action for Browser Entries

**Current State**:

- Users can click files/folders only in the explorer canvas (if a window is already open)
- No inline action in the left-pane browser to open a specific item
- Discovery of the explorer canvas feature is limited to activity-based auto-open or documentation

**Expected State**:

- Each folder/file entry has a secondary action button (→ icon, "explore" label, or right-click context menu option)
- Clicking it invokes `openInCanvas(entry.path)` to open in a floating explorer window
- Users can explore multiple folders side-by-side without manual window management

**Location**: `App.tsx` lines ~740–830 (browser entry rendering loop)

**Rationale**:

- Explorer canvas is powerful for side-by-side analysis but undiscoverable
- Reduces friction for multi-folder workflows
- Aligns with multi-pane design philosophy of the application

**Effort**: Medium (~40–60 lines, requires:

- Add "explore" button to each entry div
- Wire click handler to `openInCanvas()`
- Add CSS for button styling (icon or label)
- Optional: Add keyboard shortcut (e.g., Ctrl+Enter to explore selected item)

---

## 3. Error Propagation: From Silent Failures to Visibility

### Recent Changes

Refactored cache operations to throw exceptions instead of returning null:

- `ReadToolSupport.cacheFolderManifest()` now throws `UnsupportedOperationException` when CDI persistence unavailable
- Call sites catch and handle, but errors are not always logged to user

### Current Observation

Errors are silently swallowed in production use. When caching fails in a test context (no CDI), the tool continues but the failure is invisible to the user.

**Example**:

```java
try {
  support.cacheFolderManifest(projectDir, dir, result);
} catch (final UnsupportedOperationException e) {
  LOG.debugf("Skipping folder manifest cache: %s", e.getMessage());  // Only debug level
} catch (final Exception e) {
  LOG.warnf(e, "Failed to cache folder manifest: %s", e.getMessage());
}
```

### Recommendation

As per user request: "do not swallow any errors, log and propagate them through the console, as well as the chatbox in the frontend"

- Elevation of cache failures to INFO or WARN level (not just DEBUG)
- Optional: Surface critical errors to the chat interface so users are aware
- Ensure error details reach both backend logs and frontend UI when appropriate

---

## 4. Folder Summary Request Flow

### Current Architecture

```
User clicks folder in canvas
  ↓
openFolderSummary(windowId, projectDir, relativePath)
  ↓
Build full folder path and prompt
  ↓
POST /api/chat/oneshot { message: "Summarise folder: ... First call read_folder_manifest ..." }
  ↓
Backend ChatService.sendOneShotMessage()
  ↓
OllamaChatSession.sendOneShot() executes tools including read_folder_manifest
  ↓
read_folder_manifest returns structured evidence
  ↓
LLM processes and returns narrative response
  ↓
Response rendered in canvas (currently includes evidence artifacts)
```

### Observation

The instruction to the LLM ("use its evidence as the primary source") encourages literal reference to the evidence structure, which manifests as leaked markers in the response.

---

## 5. Recommendations for Next Session

### High-Priority (UX Impact)

1. **Implement folder summary post-processing** (Option A above):
   - Add response sanitization in `openFolderSummary()` before display
   - Test with common evidence patterns to ensure clean output
2. **Integrate ".." into folder listing**:
   - Move parent directory navigation into the browser entries loop
   - Maintains sort order and visual consistency
   - ~10-line change

### Medium-Priority (Feature Completeness)

3. **Add "Explore" action to browser entries**:
   - Either inline button or context menu option
   - Wires to `openInCanvas()`
   - Medium effort, significant UX improvement for multi-folder workflows

### Lower-Priority (Observability)

4. **Elevate cache error logging**:
   - Ensure failures surface in both backend logs and frontend UI
   - Makes production debugging easier
   - Aligns with user requirement: "do not swallow errors"

---

## 6. Summary for Report

These observations reveal a pattern: **the current system prioritizes capability over UX polish**. The folder summarization feature works technically but leaks implementation details. The file browser has powerful features (explorer canvas) but poor discoverability.

For the academic report, these gaps should be framed as:

- **Evidence-in-practice trade-off**: Exposing tool reasoning improves transparency but degrades UX if not filtered
- **Discoverability design**: Advanced features without clear UI affordances limit adoption
- **Error visibility**: Silent failures create gaps between actual and perceived system reliability

Addressing even 1-2 of these in the next iteration would significantly improve the overall user experience and align the implementation with the stated design goals.
