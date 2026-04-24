# API Listing Specification

## Policy

The backend API must follow these conventions:

- Method names should align with frontend signal names (for example `project:open`, `summary:file`, `chat:reset`).
- Resource classes should use a single fully qualified class-level path annotation:
  - `@jakarta.ws.rs.Path("/api/<namespace>{operation: (/.*)?}")`
- Method-level `@Path` annotations are avoided.
- HTTP verb action annotations are still required on action methods (`@GET`, `@POST`, `@PUT`, `@DELETE`).
- `@Transactional` annotations remain required on action methods that mutate state or rely on transactional repository access.
- Each resource may expose a small number of dispatch entrypoints per HTTP verb, which route by `operation` suffix to signal-aligned action methods.

## Frontend Signal Mapping

- `telemetry` -> `GET /api/telemetry`
- `workspace` -> `GET /api/workspace`
- `workspace:list` -> `GET /api/workspace/list`
- `project:open` -> `POST /api/project/open` (directory in body)
- `project:close` -> `DELETE /api/project/close` (projectId in body)
- `project:remote` -> `POST /api/project/remote` (projectId in body)
- `project:browse` -> `POST /api/project/browse` (projectId and optional parentUuid in body)
- `project:file` -> `POST /api/project/file` (projectId and entryUuid in body)
- `project:raw` -> `GET /api/project/{id}/raw?path=<absolute-path>`
- `summary:folder` -> `PUT /api/summary/folder` (projectId and entryUuid in body)
- `summary:file` -> `PUT /api/summary/file` (projectId and entryUuid in body)
- `summary:status` -> `PUT /api/summary/status` (projectId and entryUuid in body)
- `chat:stream` -> `POST /api/chat/stream`
- `chat:reset` -> `POST /api/chat/reset`
- `menu:*` -> `GET|POST /api/menu/<operation>` under the same namespaced policy

## Dispatch Model

- Path suffix (for example `/open`, `/list`, `/status`) is captured by the class-level regex path parameter (`operation`).
- Public dispatcher methods provide the effective runtime endpoint bindings.
- Dispatcher methods route requests to internal signal-aligned action methods that carry the HTTP verb annotations.
- Transactional behavior is applied at the public dispatcher entrypoint level where Quarkus interception is effective.
- Non-public action methods are not relied on for JAX-RS endpoint registration.
- Unsupported suffixes must throw `NotFoundException`.
