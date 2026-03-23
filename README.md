# Project Discovery in High Definition (PDHD)

PDHD is a Quarkus application for exploring local projects with a web UI and an Ollama-powered assistant.

## Prerequisites

- Java 25
- Node.js and npm (for frontend development/build)
- Optional: Ollama (for assistant chat features)
- Optional: GitHub CLI (`gh`) for GitHub repository metadata lookup

## Run in Development Mode

```sh
./mvnw quarkus:dev
```

The app runs on `http://localhost:8080`.

## Build and Run the Runnable JAR

Build:

```sh
./mvnw package
```

Run:

```sh
java -jar target/pdhd-0.1.0-runner.jar
```

The jar starts in production mode and listens on `http://0.0.0.0:8080`.

## Application Modes

When started, the CLI shows:

- `1` Launch assistant
- `2` Launch web UI
- `3` Configure Ollama
- `4` Configure system prompt
- `5` Exit

## Web UI Notes

- The top bar shows the current working folder.
- Clicking `Refresh` in the Projects panel calls `/api/projects`.
- Refresh scans from the current working folder and discovers Git projects recursively.
- Clicking a folder in the explorer runs a one-shot assistant query and shows a summary of files in that folder.
- Clicking a file in the explorer shows the file contents.
- Explorer icons are action-oriented:
  - `▸` folder (runs folder summary)
  - `●` file (opens file content)

## Screenshots

Add screenshots to `docs/screenshots/` and update the image links below.

### Main Workspace

![Main workspace](docs/screenshots/main-workspace.png)

### Project Explorer and File View

![Project explorer and file view](docs/screenshots/project-explorer-file-view.png)

### Folder Summary Interaction

![Folder summary interaction](docs/screenshots/folder-summary.png)

### Assistant Chat Panel

![Assistant chat panel](docs/screenshots/assistant-chat-panel.png)

## Assistant Tools Added

The assistant now includes additional exploration/navigation tools:

- `list_folder`: recursively lists files in a folder.
- `explain_tool`: detailed analysis of a file or directory.
- `summarise_tool`: concise summary of a file or directory.
- `navigate_tool`: changes the assistant working directory (absolute or relative path).

After `navigate_tool` runs:

- `get_cwd` reflects the new working directory.
- Path-based tools default to the new working directory when `path` is omitted.
- The Web UI "Working Folder" display updates via `/api/cwd` polling.

## Run Tests

Run all tests:

```sh
./mvnw test
```

Run a focused test class:

```sh
./mvnw -Dtest=ProjectApiResourceTest test
```

## Build Frontend Assets

```sh
cd src/main/webui
npm install
npm run build
```

## Troubleshooting

- If assistant model calls fail, verify Ollama endpoint/model settings in the configuration menu.
- If no projects appear, click `Refresh`; the current working folder should be auto-added on first run.
