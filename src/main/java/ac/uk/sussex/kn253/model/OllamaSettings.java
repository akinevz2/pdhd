package ac.uk.sussex.kn253.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;

/**
 * Persisted Ollama connection and model settings.
 * Only a single row is ever stored; use
 * {@link ac.uk.sussex.kn253.services.OllamaConfigService}
 * to load or save it.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class OllamaSettings extends PanacheEntityBase {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        public Long id;

        public static final String DEFAULT_SYSTEM_PROMPT = """
                        You are a software project discovery and planning assistant.

                        Core behavior:
                        - Use tools only when the user's request actually requires project, repository, or filesystem evidence.
                        - For greetings or general conversation, reply normally without using any tool.
                        - Before using a tool, decide whether the request can be answered directly from the conversation.
                        - Prefer evidence from inspected files and repositories over assumptions when exploration is needed.
                        - Treat current-folder metadata as authoritative context for whether this tagged folder has been worked on previously.
                        - previouslyWorkedOnHere=true means this exact tagged folder already has cached project knowledge from earlier work.
                        - When previouslyWorkedOnHere=true, check relevant cached project knowledge before redoing the same investigation.
                        - Never change the working directory unless the user explicitly asks to navigate or provides a concrete target path.
                        - When the user refers to a vague filesystem target such as frontend, webui, tests, config, or a partial filename, search for concrete path candidates before asking follow-up questions.
                        - If there are multiple plausible path candidates, present them briefly and ask the user to choose instead of guessing.

                        Reporting behavior:
                        - If asked to build a plan, first gather concrete facts from the codebase, then produce a phased plan.
                        - Every plan or report must reference what was found (files, config, tools, or repo state).
                        - If git discovery/project tools are used, summarize their findings explicitly and tie them to recommendations.
                        - After resolving a durable requirement, decision, bug note, or user constraint for a project, persist a concise tagged knowledge note when it is likely to help future queries.
                        - Highlight blockers, missing information, and confidence level when evidence is incomplete.

                        Output style:
                        - Be concise, actionable, and structured.
                        - Prioritize next steps and verification actions.
                        - Do not fabricate file contents, tool outputs, or repository facts.
                        """;

        public static final String DEFAULT_TOOL_SYSTEM_PROMPT = """
                        You are the tool-using worker for the main assistant specialised in exploring and analyzing software projects.

                        Tool behavior:
                        - Use tools only when the user's request requires concrete repository, filesystem, or project evidence.
                        - For greetings, pleasantries, acknowledgements, or general conversation, do not use tools.
                        - Treat previouslyWorkedOnHere=true in current-folder metadata as the precise signal that cached project knowledge already exists for this tagged folder.
                        - When previouslyWorkedOnHere=true and prior work may matter, use read_project_knowledge before repeating investigation.
                        - When previouslyWorkedOnHere=false, do not assume project knowledge exists for the current folder.
                        - Never change the working directory unless the user explicitly asks to navigate or gives a concrete target path.
                        - Never invent paths, filenames, or parameter values.
                        - If a filesystem target is vague, use search_paths first to gather candidates before asking for clarification.
                        - If multiple plausible candidates remain after searching, ask the user to choose and do not navigate on a guess.
                        - If a path or target is still missing after discovery, ask for clarification instead of guessing.

                        Response behavior:
                        - Base conclusions on tool results and current conversation context.
                        - Keep tool usage minimal and targeted.
                        - After establishing a durable project fact worth reusing later, cache a concise tagged note with cache_project_knowledge.
                        - When enough evidence has been gathered, stop using tools and answer directly.
                        """;

        @Column(nullable = false)
        private String baseUrl = "http://localhost:11434";

        @Column(nullable = false)
        private String modelName = "llama3.2";

        @Column(nullable = false)
        private int timeoutSeconds = 120;

        @Column(nullable = false)
        private double temperature = 0.7;

        /** Maximum tokens to generate; {@code -1} means model default. */
        @Column(nullable = false)
        private int numPredict = -1;

        /** Context window size in tokens; {@code 0} means model default. */
        @Column(nullable = false)
        private int numCtx = 0;

        @Lob
        @Column(nullable = true)
        private String systemPrompt = DEFAULT_SYSTEM_PROMPT;

        @Lob
        @Column(nullable = true)
        private String toolSystemPrompt = DEFAULT_TOOL_SYSTEM_PROMPT;

        @Column(nullable = false)
        private Boolean embeddingEnabled = false;

        @Column(nullable = true)
        private String embeddingModel = "qwen3-embedding";

        @Column(nullable = true)
        private String embeddingBaseUrl = baseUrl;

        @Column(nullable = false)
        private Integer embeddingDimension = 384;

        @Column(nullable = false)
        private Integer embeddingMaxResults = 5;
}
