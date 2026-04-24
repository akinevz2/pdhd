# Search the web for `{query}` and return a summary of the results.

## Purpose

Issue a web search for a natural-language query and return a structured summary
of the top results. The agent uses this when project inspection reveals
technologies, error messages, or concepts that require external context not
present in the local codebase.

---

## Inputs

| Parameter    | Type    | Required | Description                                                                                                         |
| ------------ | ------- | -------- | ------------------------------------------------------------------------------------------------------------------- |
| `query`      | string  | Yes      | The search query.                                                                                                   |
| `maxResults` | integer | No       | Number of search result snippets to return. Range: 1–10. Default: 5.                                                |
| `summarise`  | boolean | No       | When `true`, pass the raw snippets through the chat model and return a single synthesised summary. Default: `true`. |

---

## Expected Output

### When `summarise = true`

A single synthesised paragraph (or short section) grounded in the top search
results:

```
Web search: "Quarkus @ApplicationScoped bean proxying"

Summary:
Quarkus proxies @ApplicationScoped beans through a subclass-based interceptor.
Direct field access to injected dependencies on a manually-constructed instance
(bypassing CDI) will find those fields null because the proxy mechanism has not
been activated...

Sources:
  1. https://quarkus.io/guides/cdi-reference
  2. https://stackoverflow.com/questions/...
```

### When `summarise = false`

The raw snippets from the top `maxResults` results, with title, URL, and
excerpt:

```
Web search: "Quarkus @ApplicationScoped bean proxying"  (5 results)

1. Quarkus CDI Reference — https://quarkus.io/guides/cdi-reference
   "All normal scoped beans (e.g. @ApplicationScoped) are represented by a
    client proxy..."

2. ...
```

---

## Behaviour Rules

1. `query` must not be blank. If blank, return:
   `"Error: search query is blank"`.
2. The tool must not construct or follow URLs beyond those returned by the
   configured search provider.
3. When `summarise` is `true`, the synthesised summary must be grounded in the
   returned snippets only — the model must not add information not present in
   the search results.
4. Source URLs must always be included in the output, regardless of
   `summarise` mode, so the caller can verify the provenance of information.
5. Results are ordered by the search provider's relevance ranking; the tool
   does not re-rank them.
6. If the search provider is unavailable or returns an error, propagate a
   descriptive error rather than returning an empty result set silently.
7. The tool must not cache or persist search results beyond the current
   request.

---

## Edge Cases

| Situation                                | Expected behaviour                                                                                                                     |
| ---------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| No results found                         | Return an empty result set with a note; no error.                                                                                      |
| Search provider rate-limited             | Return: `"Error: web search rate-limited. Retry after <seconds>s."` if the header is available; otherwise a generic unavailable error. |
| `summarise = true` but model unavailable | Fall back to `summarise = false` behaviour and append a note: `"(summarisation unavailable; raw results shown)"`.                      |
| Query contains only stop words           | Proceed normally; the search provider handles query quality.                                                                           |
