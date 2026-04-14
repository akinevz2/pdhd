package ac.uk.sussex.kn253.services.ai;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

public class WebUiChatMemoryProviderSupplier implements Supplier<ChatMemoryProvider> {

    private static final int MAX_MESSAGES = 40;
    private static final Map<Object, ChatMemory> MEMORIES = new ConcurrentHashMap<>();

    @Override
    public ChatMemoryProvider get() {
        return memoryId -> MEMORIES.computeIfAbsent(memoryId, id -> MessageWindowChatMemory.builder()
                .id(id)
                .maxMessages(MAX_MESSAGES)
                .build());
    }

    public static void clear(final Object memoryId) {
        final ChatMemory removed = MEMORIES.remove(memoryId);
        if (removed != null) {
            removed.clear();
        }
    }
}
