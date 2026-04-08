package ac.uk.sussex.kn253.services;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import ac.uk.sussex.kn253.tools.FolderSummaryTools;
import ac.uk.sussex.kn253.util.JsonMarkdownRenderer;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.service.*;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Summarises folders by first extracting a structured intermediate
 * representation
 * via JSON-schema-backed AI Service output, then rendering markdown
 * deterministically.
 */
@ApplicationScoped
public class FolderSummaryService {

        @Inject
        ChatModel chatModel;

        @Inject
        FolderSummaryTools folderSummaryTools;

        @Inject
        ObjectMapper objectMapper;

        private FolderStructureAssistant assistant;
        private JsonMarkdownRenderer markdownRenderer;

        @PostConstruct
        void init() {
                assistant = AiServices.builder(FolderStructureAssistant.class)
                                .chatModel(chatModel)
                                .tools(folderSummaryTools)
                                .build();
                markdownRenderer = new JsonMarkdownRenderer(objectMapper);
        }

        public String summarizeFolder(final String request, final String folderPath) {
                final FolderIntermediateRepresentation structured = assistant.extractFolderRepresentation(request,
                                folderPath);
                return markdownRenderer.render(structured, "Folder Summary");
        }

        private interface FolderStructureAssistant {

                @SystemMessage("""
                                                Build a structured intermediate representation of folder evidence.
                                                Always do the following in order:
                                                1) Call read_folder_manifest(folderPath) exactly once.
                                                2) Call list_folder_files(folderPath) exactly once.
                                                3) Call read_folder_file(folderPath, relativePath) repeatedly for the most representative files.
                                                4) Use only evidence from tool responses; do not invent files, technologies, or risks.
                                                5) Do not include recommendations or next steps.
                                """)
                FolderIntermediateRepresentation extractFolderRepresentation(
                                @UserMessage String request,
                                @V("folderPath") String folderPath);
        }

        @Description("Structured folder analysis extracted from folder tools")
        private record FolderIntermediateRepresentation(
                        @Description("Absolute or resolved folder path") String folderPath,
                        @Description("Concise description of folder structure and composition") String structureOverview,
                        @Description("Likely technologies/frameworks inferred from read files") List<String> probableTechnologies,
                        @Description("Key files with roles and supporting evidence") List<KeyFileObservation> keyFiles,
                        @Description("Notable risks inferred from evidence") List<RiskObservation> notableRisks,
                        @Description("Short evidence snippets grounding the analysis") List<String> evidenceSnippets) {
        }

        @Description("A key file discovered during folder analysis")
        private record KeyFileObservation(
                        @Description("Relative path of the file") String relativePath,
                        @Description("Role this file plays in the folder") String role,
                        @Description("Evidence supporting this role assignment") String evidence) {
        }

        @Description("A concrete risk observation from folder evidence")
        private record RiskObservation(
                        @Description("Severity label, such as low, medium, or high") String severity,
                        @Description("Description of the risk") String risk,
                        @Description("Evidence supporting the risk") String evidence) {
        }
}
