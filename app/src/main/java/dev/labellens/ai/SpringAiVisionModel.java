package dev.labellens.ai;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;

import java.util.List;

/**
 * The real model, called through Spring AI.
 * This is the ONLY class in the project that uses Spring AI types.
 *
 * Connection settings (Azure endpoint, key, deployment) are in application.yml under spring.ai.openai.
 */
@Component
public class SpringAiVisionModel implements VisionModel {

    private final ChatModel chatModel;
    private final String deployment;

    public SpringAiVisionModel(ChatModel chatModel,
                               @Value("${spring.ai.openai.chat.model}") String deployment) {
        this.chatModel = chatModel;
        this.deployment = deployment;
    }

    @Override
    public ModelReply ask(String instructions, List<ImageInput> images) {
        List<Media> media = images.stream()
                .map(image -> new Media(MimeType.valueOf(image.contentType()), new ByteArrayResource(image.data())))
                .toList();

        List<Message> messages = List.of(
                new SystemMessage(instructions),
                UserMessage.builder()
                        .text("Here are the photos of the food label.")
                        .media(media)
                        .build());

        ChatResponse response = chatModel.call(new Prompt(messages));

        String text = response.getResult().getOutput().getText();
        Usage usage = response.getMetadata().getUsage();
        String model = response.getMetadata().getModel();
        return new ModelReply(
                text,
                model != null && !model.isBlank() ? model : deployment,
                usage != null ? usage.getPromptTokens() : null,
                usage != null ? usage.getCompletionTokens() : null);
    }

    @Override
    public String name() {
        return deployment;
    }
}
