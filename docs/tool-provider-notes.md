# Tool Provider Deactivation Notes

The `*ToolProvider` classes have been reduced to CDI-only shells. This note records what each provider used to do and the Linux CLI commands that would most closely approximate the same outcome.

| Provider                                 | Intended tool behavior                              | Approximate Linux CLI commands                                          |
| ---------------------------------------- | --------------------------------------------------- | ----------------------------------------------------------------------- |
| `AnalyzePathDetailedToolProvider`        | Detailed analysis of a file or directory path       | `realpath`, `stat`, `file`, `ls -la`, `find`                            |
| `ChangeWorkingDirectoryToolProvider`     | Change working directory                            | `cd`, `pwd`                                                             |
| `GetCurrentWorkingDirectoryToolProvider` | Return current working directory                    | `pwd`                                                                   |
| `GetGitLogToolProvider`                  | Show recent git commits                             | `git log --oneline`, `git log --stat`                                   |
| `GetPathInfoToolProvider`                | Return path metadata                                | `realpath`, `stat`, `test -e`, `test -d`, `test -f`                     |
| `ListFilesRecursiveToolProvider`         | Recursively list files                              | `find <path> -type f`, `rg --files`                                     |
| `ListGitProjectsToolProvider`            | Discover git repositories                           | `find <root> -type d -name .git`                                        |
| `ListGithubProjectsToolProvider`         | Discover repos with GitHub remotes                  | `find`, `git remote get-url origin`, `grep github.com`                  |
| `ListProjectEntriesToolProvider`         | List immediate project directory entries            | `ls -la`, `find <path> -maxdepth 1`                                     |
| `ListSubdirectoriesToolProvider`         | List immediate subdirectories                       | `find <path> -mindepth 1 -maxdepth 1 -type d`                           |
| `ResolvePathToolProvider`                | Normalize a path against cwd                        | `realpath`, `readlink -f`                                               |
| `SearchPathsToolProvider`                | Search for likely filesystem matches                | `find`, `rg`, `grep`                                                    |
| `SummarizePathToolProvider`              | Summarize a file or directory from sampled contents | `ls -la`, `find`, `head`, `sed -n`                                      |
| `GetEmbeddingContextToolProvider`        | Query semantic context from embeddings              | No direct Linux CLI equivalent; application-level vector search         |
| `GetRecentEmbeddingsToolProvider`        | Return recent embeddings                            | No direct Linux CLI equivalent; application-level embedding store query |
| `GetSessionContextToolProvider`          | Return cwd and recent session context               | `pwd`, shell history inspection, application logs                       |
| `OpenWorkspaceCanvasToolProvider`        | Open a workspace path in the UI                     | `xdg-open`, `open`, or `"$BROWSER" <url>`                               |
| `ReadFolderManifestToolProvider`         | Read a folder tree plus sampled contents            | `find`, `tree`, `sed -n`, `head`                                        |
| `ReadProjectKnowledgeToolProvider`       | Read cached project knowledge                       | `cat`, `sed -n`, `find .pdhd`                                           |
| `ReadProjectManifestToolProvider`        | Read project identity files and source layout       | `cat README*`, `cat pom.xml`, `find src`, `sed -n`                      |
| `ReadFileToolProvider`                   | Read a text file                                    | `cat`, `sed -n`, `head`, `tail`                                         |
| `AppendProjectTodoToolProvider`          | Append to `TODO.md`                                 | `printf >> TODO.md`, `tee -a TODO.md`                                   |
| `CacheProjectKnowledgeToolProvider`      | Persist project knowledge note                      | `mkdir -p`, `printf >>`, `tee -a`                                       |
| `CreatePlanToolProvider`                 | Create a plan markdown file                         | `mkdir -p`, `cat > file`, `tee`                                         |
| `CreateReportToolProvider`               | Create a report markdown file                       | `mkdir -p`, `cat > file`, `tee`                                         |
| `CreateTimelineToolProvider`             | Create a timeline markdown file                     | `mkdir -p`, `cat > file`, `tee`                                         |
| `WriteFileToolProvider`                  | Write a file in a project                           | `cat > file`, `tee`, `printf > file`                                    |
