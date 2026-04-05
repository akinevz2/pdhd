package ac.uk.sussex.kn253.schema;

/**
 * Canonical schema key names shared across API payloads, settings fields, and
 * cached project-knowledge JSON.
 */
public final class SchemaKeys {

    private SchemaKeys() {
    }

    public static final String ID = "id";
    public static final String KEY = "key";
    public static final String NAME = "name";
    public static final String PATH = "path";
    public static final String DIRECTORY = "directory";
    public static final String CWD = "cwd";
    public static final String STATUS = "status";
    public static final String MODELS = "models";

    public static final String PROJECT = "project";
    public static final String PROJECT_DIRECTORY = "projectDirectory";
    public static final String DIRS = "dirs";
    public static final String REPO_URL = "repoUrl";
    public static final String ENTRIES = "entries";

    public static final String JSON_CONTENT = "jsonContent";
    public static final String UPDATED_AT = "updatedAt";

    public static final String TAG = "tag";
    public static final String TAGS = "tags";
    public static final String TIMESTAMP = "timestamp";
    public static final String SOURCE = "source";
    public static final String QUERY = "query";
    public static final String NOTE = "note";
    public static final String NEXT = "next";

    public static final String FOLDER_ID = "currentPathId";
    public static final String FOLDER_DIR = "currentPathDir";
    public static final String HAS_GIT = "hasGit";
    public static final String TAG_COUNT = "tagCount";
    public static final String HAS_HISTORY = "hasHistory";
    public static final String ASSISTANT_ACTION_POLICY = "assistantActionPolicy";
    public static final String ASSISTANT_ACTION_BLOCK_LANGUAGE = "assistantActionBlockLanguage";
    public static final String ASSISTANT_ACTION_BLOCK_TEMPLATE = "assistantActionBlockTemplate";

    public static final String FILE_PATH = "filePath";
    public static final String CONTENT_LENGTH = "contentLength";
    public static final String CONTENT = "content";
    public static final String TYPE = "type";
    public static final String CACHED_AT = "cachedAt";
    public static final String TTL_SECONDS = "ttlSeconds";
    public static final String TARGET_PATH = "targetPath";
    public static final String ANALYSIS_TYPE = "analysisType";
    public static final String ANALYSIS_RESULT = "analysisResult";
    public static final String FOLDER_PATH = "folderPath";
    public static final String MANIFEST_CONTENT = "manifestContent";

    public static final String SETTINGS = "settings";
    public static final String SETTING_FIELDS = "settingFields";
    public static final String INPUT_TYPE = "inputType";

    public static final String FIELD_BASE_URL = "baseUrl";
    public static final String FIELD_MODEL_NAME = "modelName";
    public static final String FIELD_TIMEOUT_SECONDS = "timeoutSeconds";
    public static final String FIELD_TEMPERATURE = "temperature";
    public static final String FIELD_NUM_PREDICT = "numPredict";
    public static final String FIELD_NUM_CTX = "numCtx";
    public static final String FIELD_SYSTEM_PROMPT = "systemPrompt";
    public static final String FIELD_TOOL_SYSTEM_PROMPT = "toolSystemPrompt";
    public static final String FIELD_EMBEDDING_ENABLED = "embeddingEnabled";
    public static final String FIELD_EMBEDDING_MODEL = "embeddingModel";
    public static final String FIELD_EMBEDDING_BASE_URL = "embeddingBaseUrl";
    public static final String FIELD_EMBEDDING_DIMENSION = "embeddingDimension";
    public static final String FIELD_EMBEDDING_MAX_RESULTS = "embeddingMaxResults";

    // Provider selection
    public static final String FIELD_PROVIDER = "provider";

    // OpenAI-specific
    public static final String FIELD_API_KEY = "apiKey";
    public static final String FIELD_MAX_TOKENS = "maxTokens";
    public static final String FIELD_PRESENCE_PENALTY = "presencePenalty";
    public static final String FIELD_FREQUENCY_PENALTY = "frequencyPenalty";
    public static final String FIELD_MAX_RETRIES = "maxRetries";

    // Shared sampling
    public static final String FIELD_TOP_P = "topP";

    // Ollama-specific sampling
    public static final String FIELD_TOP_K = "topK";
    public static final String FIELD_SEED = "seed";

    // Diagnostics
    public static final String FIELD_LOG_REQUESTS = "logRequests";
    public static final String FIELD_LOG_RESPONSES = "logResponses";
}