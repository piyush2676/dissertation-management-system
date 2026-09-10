package com.dms.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Whether the AI features can run at all.
 *
 * <p>Everything here is optional by design: the rest of the system must start and
 * work with no API key configured. Models are taken as {@link ObjectProvider} so a
 * missing or failed auto-configuration is a disabled feature and an honest message
 * on the page, never a broken application.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AiAvailability {

    private final ObjectProvider<EmbeddingModel> embeddingModels;
    private final ObjectProvider<ChatModel> chatModels;

    /** Similarity features: supervisor matching, archive overlap. */
    public boolean embeddingsAvailable() {
        return embeddingModels.getIfAvailable() != null;
    }

    /** The narrative half: the written novelty report. */
    public boolean chatAvailable() {
        return chatModels.getIfAvailable() != null;
    }

    public EmbeddingModel embeddingModel() {
        EmbeddingModel model = embeddingModels.getIfAvailable();
        if (model == null) {
            throw new AiUnavailableException(
                    "No embedding model is configured. Set a Google AI Studio key to enable this.");
        }
        return model;
    }

    public ChatModel chatModel() {
        ChatModel model = chatModels.getIfAvailable();
        if (model == null) {
            throw new AiUnavailableException(
                    "No chat model is configured. Set a Google AI Studio key to enable this.");
        }
        return model;
    }
}
